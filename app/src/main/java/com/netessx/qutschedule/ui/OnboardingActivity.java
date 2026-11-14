package com.netessx.qutschedule.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.auto.AutoRefreshJobService;
import com.netessx.qutschedule.auto.AutoRefreshScheduler;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.data.ZfCaptureStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.model.Profile;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;

/**
 * 初次使用引导：设置学期 → 登录教务系统 → 设置自动刷新 → 用法速览 → 楼层错峰说明 → 个人信息。
 *
 * <p>每一步都可以跳过；点「跳过」或走完最后一步都会把 {@code prefs.onboarded} 置为 true。
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int STEP_WELCOME = 0;
    private static final int STEP_ZF_LOGIN = 1;
    private static final int STEP_SEMESTER = 2;
    private static final int STEP_AUTO_REFRESH = 3;
    private static final int STEP_TIPS = 4;
    private static final int STEP_FLOOR = 5;
    private static final int STEP_SETTINGS = 6;
    private static final int STEP_PROFILE = 7;
    private static final int STEP_DONE = 8;
    private static final int STEP_COUNT = 9;

    private AppData data;
    private Prefs prefs;
    private Semester semester;

    private FrameLayout content;
    private TextView stepView;
    private MaterialButton backButton;
    private MaterialButton nextButton;

    private int step = STEP_WELCOME;

    private TextInputLayout nameInput;
    private TextInputLayout weeksInput;
    private MaterialButton startButton;
    private TextInputLayout nicknameInput;
    private TextInputLayout schoolInput;
    private TextInputLayout collegeInput;
    private TextInputLayout majorInput;
    private TextInputLayout gradeInput;

    private final ActivityResultLauncher<Intent> zfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // 抓取成功后自动进入学期确认，不用再点「下一步」
                    step = STEP_SEMESTER;
                }
                render();
            });

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    public static Intent newIntent(Context ctx) {
        return new Intent(ctx, OnboardingActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        Insets.applySystemBars(this, findViewById(R.id.root));

        data = ScheduleStore.get(this).data();
        prefs = data.prefs;
        semester = ScheduleStore.get(this).currentSemester();

        content = findViewById(R.id.content);
        stepView = findViewById(R.id.tv_step);
        backButton = findViewById(R.id.btn_back);
        nextButton = findViewById(R.id.btn_next);
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
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 引导必须走完：只能回上一步，不能直接退出
                if (step > STEP_WELCOME) {
                    step--;
                    render();
                }
            }
        });
        render();
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
        updateNextState();
    }

    /** 第 2 步要先登录抓到课表，第 3 步要先设置开学日期，否则不放行。 */
    private void updateNextState() {
        boolean enabled = true;
        if (step == STEP_ZF_LOGIN) {
            enabled = ZfCaptureStore.get(this).hasCapture();
        } else if (step == STEP_SEMESTER) {
            enabled = semester.startMonday() != null;
        }
        nextButton.setEnabled(enabled);
        nextButton.setAlpha(enabled ? 1f : 0.4f);
    }

    private View buildPage(int which) {
        switch (which) {
            case STEP_SEMESTER:
                return buildSemesterPage();
            case STEP_ZF_LOGIN:
                return buildZfLoginPage();
            case STEP_AUTO_REFRESH:
                return buildAutoRefreshPage();
            case STEP_TIPS:
                return simplePage(R.string.onboarding_tips_title, R.string.onboarding_tips_body);
            case STEP_FLOOR:
                return simplePage(R.string.onboarding_floor_title, R.string.onboarding_floor_body);
            case STEP_SETTINGS:
                return simplePage(R.string.onboarding_settings_title,
                        R.string.onboarding_settings_body);
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
        heading(column, R.string.onboarding_semester_title,
                semester.isDefaultName()
                        ? R.string.onboarding_semester_body
                        : R.string.onboarding_semester_body_auto);

        nameInput = SettingsUi.textRow(this, getString(R.string.settings_term_name), semester.name);
        column.addView(nameInput);

        column.addView(SettingsUi.label(this, getString(R.string.onboarding_semester_start)));
        startButton = SettingsUi.buttonRow(this, termStartText(), v -> pickStartDate());
        column.addView(startButton);

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
            updateNextState();
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

    // ---------- 登录教务系统 ----------

    private View buildZfLoginPage() {
        LinearLayout column = pageColumn();
        heading(column, R.string.onboarding_zf_title, R.string.onboarding_zf_body);
        column.addView(SettingsUi.buttonRow(this, getString(R.string.onboarding_zf_button),
                v -> zfLauncher.launch(new Intent(this, WebImportActivity.class)
                        .putExtra(WebImportActivity.EXTRA_AUTO_IMPORT, true))));
        boolean captured = ZfCaptureStore.get(this).hasCapture();
        column.addView(SettingsUi.label(this, getString(captured
                ? R.string.onboarding_zf_done : R.string.onboarding_zf_pending)));
        return scrollHost(column);
    }

    // ---------- 自动刷新 ----------

    private View buildAutoRefreshPage() {
        LinearLayout column = pageColumn();
        heading(column, R.string.onboarding_auto_title, R.string.onboarding_auto_body);
        AutoRefreshSettingsView view = new AutoRefreshSettingsView(this);
        view.bind(prefs, ZfCaptureStore.get(this).hasCapture(),
                new AutoRefreshSettingsView.Listener() {
                    @Override
                    public void onChanged() {
                        ScheduleStore.get(OnboardingActivity.this).save();
                        AutoRefreshScheduler.reschedule(OnboardingActivity.this);
                        if (prefs.autoRefreshEnabled) {
                            ensureNotificationPermission();
                        }
                    }

                    @Override
                    public void onRefreshNow() {
                        ensureNotificationPermission();
                        AutoRefreshJobService.enqueue(OnboardingActivity.this);
                    }
                });
        column.addView(view);
        column.addView(SettingsUi.label(this, getString(R.string.onboarding_auto_skip)));
        return scrollHost(column);
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ---------- 个人信息 ----------

    private View buildProfilePage() {
        Profile profile = data.profile;
        LinearLayout column = pageColumn();
        heading(column, R.string.onboarding_profile_title, R.string.onboarding_profile_body);

        nicknameInput = SettingsUi.textRow(this, getString(R.string.profile_nickname),
                profile.nickname);
        column.addView(nicknameInput);

        schoolInput = SettingsUi.textRow(this, getString(R.string.profile_school), profile.school);
        column.addView(schoolInput);

        collegeInput = SettingsUi.textRow(this, getString(R.string.profile_college),
                profile.college);
        column.addView(collegeInput);

        majorInput = SettingsUi.textRow(this, getString(R.string.profile_major), profile.major);
        column.addView(majorInput);

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
