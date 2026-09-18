package com.netessx.qutschedule.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.pdf.TimetableParser;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 导入课表 PDF：解析后先预览，再决定覆盖或追加。 */
public class ImportActivity extends BaseActivity {

    private static final int MAX_PDF_BYTES = 16 * 1024 * 1024;
    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Course> parsed = new ArrayList<>();
    private final List<Course> parsedUnscheduled = new ArrayList<>();

    private TextView status;
    private Button mergeButton;
    private Button replaceButton;
    private PreviewAdapter adapter;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    parse(uri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(getString(R.string.title_import));
        setPageContent(getLayoutInflater().inflate(R.layout.activity_import, null, false));

        status = findViewById(R.id.tv_status);
        mergeButton = findViewById(R.id.btn_merge);
        replaceButton = findViewById(R.id.btn_replace);

        adapter = new PreviewAdapter();
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        findViewById(R.id.btn_pick).setOnClickListener(v ->
                picker.launch(new String[]{"application/pdf"}));
        mergeButton.setOnClickListener(v -> commit(false));
        replaceButton.setOnClickListener(v -> commit(true));
        setButtonsEnabled(false);

        if (!ScheduleStore.get(this).currentSemester().isConfigured()) {
            status.setText(R.string.import_need_term);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void setButtonsEnabled(boolean enabled) {
        mergeButton.setEnabled(enabled);
        replaceButton.setEnabled(enabled);
    }

    private void parse(final Uri uri) {
        status.setText(R.string.import_parsing);
        setButtonsEnabled(false);
        parsed.clear();
        parsedUnscheduled.clear();
        adapter.notifyDataSetChanged();

        executor.execute(() -> {
            String error = null;
            TimetableParser.ParseResult result = null;
            try {
                result = TimetableParser.parse(readAll(uri));
                if (!result.isOk()) {
                    error = result.error;
                }
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String finalError = error;
            final TimetableParser.ParseResult finalResult = result;
            runOnUiThread(() -> onParsed(finalResult, finalError));
        });
    }

    private void onParsed(TimetableParser.ParseResult result, String error) {
        if (error != null || result == null) {
            status.setText(getString(R.string.import_failed, String.valueOf(error)));
            setButtonsEnabled(false);
            return;
        }
        Semester semester = ScheduleStore.get(this).currentSemester();
        int maxWeek = Math.max(semester.totalWeeks, 30);
        for (Course course : result.courses) {
            if (semester.isConfigured()) {
                course.weeks.removeIf(week -> week < 1 || week > maxWeek);
            }
            parsed.add(course);
        }
        parsedUnscheduled.addAll(result.unscheduled);

        // 课表 PDF 里没有开学日期，只能带出学期名，周次仍由用户在学期设置里填的日期推算
        if (!result.termName.isEmpty() && (semester.name == null || semester.name.isEmpty())) {
            semester.name = result.termName;
        }

        status.setText(getString(R.string.import_summary_fmt, parsed.size(), parsedUnscheduled.size()));
        adapter.notifyDataSetChanged();
        setButtonsEnabled(!parsed.isEmpty() || !parsedUnscheduled.isEmpty());
    }

    private void commit(boolean replace) {
        ScheduleStore store = ScheduleStore.get(this);
        AppData data = store.data();
        Semester semester = store.currentSemester();

        if (replace) {
            data.courses.removeIf(course -> semester.id.equals(course.semesterId));
        } else {
            data.courses.removeIf(course -> semester.id.equals(course.semesterId)
                    && isDuplicate(course));
        }
        for (Course course : parsed) {
            course.semesterId = semester.id;
            data.courses.add(course);
        }
        for (Course course : parsedUnscheduled) {
            course.semesterId = semester.id;
            data.courses.add(course);
        }
        store.save();
        ReminderScheduler.sync(this);
        Toast.makeText(this, R.string.import_done, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    /** 追加时用「名称 + 星期 + 起始节」判断是否为同一门课，避免重复堆叠。 */
    private boolean isDuplicate(Course existing) {
        for (Course course : parsed) {
            if (course.name.equals(existing.name)
                    && course.dayOfWeek == existing.dayOfWeek
                    && course.startSlot == existing.startSlot) {
                return true;
            }
        }
        return false;
    }

    private byte[] readAll(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("无法打开所选文件");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_PDF_BYTES) {
                    throw new IOException("文件过大");
                }
            }
            return out.toByteArray();
        }
    }

    private class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_import_course, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            List<Course> all = new ArrayList<>(parsed);
            all.addAll(parsedUnscheduled);
            Course course = all.get(position);
            holder.name.setText(course.name);
            holder.detail.setText(describe(course));
        }

        private String describe(Course course) {
            StringBuilder sb = new StringBuilder();
            if (course.dayOfWeek >= 1) {
                sb.append("周").append(DAY_NAMES[course.dayOfWeek - 1]);
                sb.append(" 第 ").append(course.startSlot).append('-').append(course.endSlot).append(" 节");
            } else {
                sb.append("未排课");
            }
            if (course.weekSpec != null && !course.weekSpec.isEmpty()) {
                sb.append(" · ").append(course.weekSpec);
            }
            if (course.location != null && !course.location.isEmpty()) {
                sb.append(" · ").append(course.location);
            }
            if (course.teacher != null && !course.teacher.isEmpty()) {
                sb.append(" · ").append(course.teacher);
            }
            return sb.toString();
        }

        @Override
        public int getItemCount() {
            return parsed.size() + parsedUnscheduled.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView detail;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tv_name);
                detail = itemView.findViewById(R.id.tv_detail);
            }
        }
    }
}
