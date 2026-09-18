package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.model.Profile;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;

/**
 * 初次使用引导：设置学期 → 下载 PDF → 导入 PDF → 个人信息。
 *
 * <p>每一步都可以跳过；点「跳过」或走完最后一步都会把 {@code prefs.onboarded} 置为 true。
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int STEP_WELCOME = 0;
    private static final int STEP_SEMESTER = 1;
    private static final int STEP_DOWNLOAD = 2;
    private static final int STEP_IMPORT = 3;
    private static final int STEP_PROFILE = 4;
    private static final int STEP_DONE = 5;
    private static final int STEP_COUNT = 6;

    /** 下载课表的一步。文字改 res/values/strings_app.xml；{@code imageName} 为 null 表示这步不放截图。 */
    private static final class DownloadStep {
        final int titleRes;
        final int bodyRes;
        final String imageName;

        DownloadStep(int titleRes, int bodyRes, String imageName) {
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
            this.imageName = imageName;
        }
    }

    /** 三步的标题、正文与配图都在这里指定；改文字去 strings_app.xml，改配图改第三个参数。 */
    private static final DownloadStep[] DOWNLOAD_STEPS = {
            new DownloadStep(R.string.onboarding_download_step1_title,
                    R.string.onboarding_download_step1_body, "onboarding_1"),
            new DownloadStep(R.string.onboarding_download_step2_title,
                    R.string.onboarding_download_step2_body, "onboarding_2"),
            new DownloadStep(R.string.onboarding_download_step3_title,
                    R.string.onboarding_download_step3_body, null),
    };

    private AppData data;
    private Prefs prefs;
    private Semester semester;

    private FrameLayout content;
    private TextView stepView;
    private MaterialButton backButton;
    private MaterialButton nextButton;

    private int step = STEP_WELCOME;
    private boolean imported;

    private TextInputLayout nameInput;
    private TextInputLayout weeksInput;
    private MaterialButton startButton;
    private TextInputLayout nicknameInput;
    private TextInputLayout schoolInput;
    private TextInputLayout collegeInput;
    private TextInputLayout majorInput;
    private TextInputLayout gradeInput;

    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    imported = true;
                    render();
                }
            });

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, OnboardingActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        Insets.applySystemBars(findViewById(R.id.root));

        data = ScheduleStore.get(this).data();
        prefs = data.prefs;
        semester = ScheduleStore.get(this).currentSemester();

        content = findViewById(R.id.content);
        stepView = findViewById(R.id.tv_step);
        backButton = findViewById(R.id.btn_back);
        nextButton = findViewById(R.id.btn_next);
        findViewById(R.id.btn_skip).setOnClickListener(v -> finishOnboarding());
        backButton.setOnClickListener(v -> {
            if (step > STEP_WELCOME) {
                step--;
                render();
            }
        });
        nextButton.setOnClickListener(v -> {
            if (step == STEP_SEMESTER) {
                // 开学日期没填就不放行：后面所有周次推算都依赖它
                if (!saveSemester()) {
                    promptForStartDate();
                    return;
                }
            } else if (step == STEP_PROFILE) {
                saveProfile();
            }
            if (step == STEP_DONE) {
                finishOnboarding();
                return;
            }
            step++;
            render();
        });
        render();
    }

    /** 用返回键退出也算看过了，否则每次启动都会再弹一次。 */
    @Override
    public void finish() {
        super.finish();
        if (!prefs.onboarded) {
            prefs.onboarded = true;
            ScheduleStore.get(this).save();
        }
    }

    private void finishOnboarding() {
        prefs.onboarded = true;
        ScheduleStore.get(this).save();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void render() {
        stepView.setText(getString(R.string.onboarding_step_fmt, step + 1, STEP_COUNT));
        backButton.setVisibility(step == STEP_WELCOME ? View.INVISIBLE : View.VISIBLE);
        nextButton.setText(step == STEP_DONE ? R.string.onboarding_start : R.string.onboarding_next);

        content.removeAllViews();
        content.addView(buildPage(step), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private View buildPage(int which) {
        switch (which) {
            case STEP_SEMESTER:
                return buildSemesterPage();
            case STEP_DOWNLOAD:
                return buildDownloadPage();
            case STEP_IMPORT:
                return buildImportPage();
            case STEP_PROFILE:
                return buildProfilePage();
            case STEP_DONE:
                return simplePage(R.string.onboarding_done_title, R.string.onboarding_done_body);
            default:
                return simplePage(R.string.onboarding_welcome_title,
                        R.string.onboarding_welcome_body);
        }
    }

    private View scrollHost(LinearLayout column) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        return scroll;
    }

    private LinearLayout pageColumn() {
        LinearLayout column = SettingsUi.column(this);
        column.setPadding(SettingsUi.dp(this, 20), SettingsUi.dp(this, 8),
                SettingsUi.dp(this, 20), SettingsUi.dp(this, 24));
        return column;
    }

    private void heading(LinearLayout column, int titleRes, int bodyRes) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, SettingsUi.dp(this, 12), 0, SettingsUi.dp(this, 8));
        column.addView(title);

        TextView body = new TextView(this);
        body.setText(bodyRes);
        body.setTextSize(14);
        body.setLineSpacing(0, 1.3f);
        body.setTextColor(getColor(R.color.colorOnSurfaceVariant));
        column.addView(body);
    }

    private View simplePage(int titleRes, int bodyRes) {
        LinearLayout column = pageColumn();
        heading(column, titleRes, bodyRes);
        return scrollHost(column);
    }

    // ---------- 学期 ----------

    private View buildSemesterPage() {
        LinearLayout column = pageColumn();
        heading(column, R.string.onboarding_semester_title, R.string.onboarding_semester_body);

        column.addView(SettingsUi.label(this, getString(R.string.onboarding_semester_name)));
        nameInput = SettingsUi.textRow(this, getString(R.string.settings_term_name), semester.name);
        column.addView(nameInput);

        column.addView(SettingsUi.label(this, getString(R.string.onboarding_semester_start)));
        startButton = SettingsUi.buttonRow(this, termStartText(), v -> pickStartDate());
        column.addView(startButton);

        column.addView(SettingsUi.label(this, getString(R.string.onboarding_semester_weeks)));
        weeksInput = SettingsUi.numberRow(this, getString(R.string.onboarding_semester_weeks),
                String.valueOf(semester.totalWeeks));
        column.addView(weeksInput);
        return scrollHost(column);
    }

    private String termStartText() {
        LocalDate start = semester.startMonday();
        if (start == null) {
            return getString(R.string.onboarding_semester_start) + "（未设置）";
        }
        return start.format(Dates.YMD) + "（周" + Dates.weekName(start) + "）";
    }

    private void pickStartDate() {
        LocalDate today = LocalDate.now();
        LocalDate current = semester.startMonday();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            LocalDate picked = LocalDate.of(year, month + 1, dayOfMonth);
            semester.startDate = Dates.mondayOf(picked).toString();
            if (startButton != null) {
                startButton.setText(termStartText());
            }
        }, current == null ? today.getYear() : current.getYear(),
                (current == null ? today.getMonthValue() : current.getMonthValue()) - 1,
                current == null ? today.getDayOfMonth() : current.getDayOfMonth()).show();
    }

    private void promptForStartDate() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.onboarding_need_start_title)
                .setMessage(R.string.onboarding_need_start_body)
                .setPositiveButton(R.string.onboarding_need_start_action,
                        (dialog, which) -> pickStartDate())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** @return 开学日期是否已设置；未设置时调用方应拦住不让进入下一步 */
    private boolean saveSemester() {
        if (nameInput != null) {
            semester.name = SettingsUi.text(nameInput);
        }
        if (weeksInput != null) {
            try {
                semester.totalWeeks = Math.max(1, Integer.parseInt(SettingsUi.text(weeksInput)));
            } catch (NumberFormatException ignored) {
                // 输入不是数字时保持原值
            }
        }
        semester.normalize();
        ScheduleStore.get(this).save();
        return semester.startMonday() != null;
    }

    // ---------- 下载 PDF ----------

    private View buildDownloadPage() {
        LinearLayout column = pageColumn();
        heading(column, R.string.onboarding_download_title, R.string.onboarding_download_body);
        for (int i = 0; i < DOWNLOAD_STEPS.length; i++) {
            column.addView(stepView(i + 1, DOWNLOAD_STEPS[i]));
        }
        column.addView(SettingsUi.label(this, getString(R.string.onboarding_download_fallback)));
        return scrollHost(column);
    }

    /** 一步：标题 + 正文，配图可选。 */
    private View stepView(int index, DownloadStep step) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, SettingsUi.dp(this, 20), 0, 0);

        TextView caption = new TextView(this);
        caption.setText(getString(R.string.onboarding_download_step_fmt, index)
                + " · " + getString(step.titleRes));
        caption.setTextSize(14);
        caption.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(caption);

        TextView body = new TextView(this);
        body.setText(step.bodyRes);
        body.setTextSize(13);
        body.setLineSpacing(0, 1.25f);
        body.setTextColor(getColor(R.color.colorOnSurfaceVariant));
        body.setPadding(0, SettingsUi.dp(this, 4), 0, 0);
        box.addView(body);

        if (step.imageName == null) {
            return box;
        }
        // 资源按名字查找，把 onboarding_1.png 丢进 res/drawable-nodpi/ 就会自动显示
        int resId = getResources().getIdentifier(step.imageName, "drawable", getPackageName());
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setMinimumHeight(SettingsUi.dp(this, 160));
        image.setImageResource(resId != 0 ? resId : R.drawable.onboarding_shot_placeholder);
        box.addView(image);

        if (resId != 0) {
            final int preview = resId;
            image.setOnClickListener(v -> ImagePreviewDialog.show(this, preview,
                    getString(R.string.onboarding_download_step_fmt, index)
                            + " · " + getString(step.titleRes)));
        } else {
            TextView hint = new TextView(this);
            hint.setText(getString(R.string.onboarding_download_shot_hint, step.imageName));
            hint.setTextSize(11);
            hint.setGravity(Gravity.CENTER);
            hint.setTextColor(getColor(R.color.colorOnSurfaceVariant));
            box.addView(hint);
        }
        return box;
    }

    // ---------- 导入 PDF ----------

    private View buildImportPage() {
        LinearLayout column = pageColumn();
        heading(column, R.string.onboarding_import_title, R.string.onboarding_import_body);
        column.addView(SettingsUi.buttonRow(this, getString(R.string.onboarding_import_button),
                v -> importLauncher.launch(new Intent(this, ImportActivity.class))));
        if (imported) {
            column.addView(SettingsUi.label(this, getString(R.string.onboarding_import_done)));
        }
        column.addView(SettingsUi.label(this, getString(R.string.onboarding_import_later)));
        return scrollHost(column);
    }

    // ---------- 个人信息 ----------

    private View buildProfilePage() {
        Profile profile = data.profile;
        LinearLayout column = pageColumn();
        heading(column, R.string.onboarding_profile_title, R.string.onboarding_profile_body);

        column.addView(SettingsUi.label(this, getString(R.string.profile_nickname)));
        nicknameInput = SettingsUi.textRow(this, getString(R.string.profile_nickname),
                profile.nickname);
        column.addView(nicknameInput);

        column.addView(SettingsUi.label(this, getString(R.string.profile_school)));
        schoolInput = SettingsUi.textRow(this, getString(R.string.profile_school), profile.school);
        column.addView(schoolInput);

        column.addView(SettingsUi.label(this, getString(R.string.profile_college)));
        collegeInput = SettingsUi.textRow(this, getString(R.string.profile_college),
                profile.college);
        column.addView(collegeInput);

        column.addView(SettingsUi.label(this, getString(R.string.profile_major)));
        majorInput = SettingsUi.textRow(this, getString(R.string.profile_major), profile.major);
        column.addView(majorInput);

        column.addView(SettingsUi.label(this, getString(R.string.profile_grade)));
        gradeInput = SettingsUi.textRow(this, getString(R.string.profile_grade), profile.grade);
        column.addView(gradeInput);
        return scrollHost(column);
    }

    private void saveProfile() {
        Profile profile = data.profile;
        if (nicknameInput != null) {
            profile.nickname = SettingsUi.text(nicknameInput);
        }
        if (schoolInput != null) {
            profile.school = SettingsUi.text(schoolInput);
        }
        if (collegeInput != null) {
            profile.college = SettingsUi.text(collegeInput);
        }
        if (majorInput != null) {
            profile.major = SettingsUi.text(majorInput);
        }
        if (gradeInput != null) {
            profile.grade = SettingsUi.text(gradeInput);
        }
        profile.normalize();
        ScheduleStore.get(this).save();
    }
}
