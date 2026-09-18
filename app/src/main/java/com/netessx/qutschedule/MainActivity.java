package com.netessx.qutschedule;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.live.LiveUpdateService;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Todo;
import com.netessx.qutschedule.reminder.ReminderScheduler;
import com.netessx.qutschedule.ui.AgendaFragment;
import com.netessx.qutschedule.ui.CourseEditActivity;
import com.netessx.qutschedule.ui.ImportActivity;
import com.netessx.qutschedule.ui.MineFragment;
import com.netessx.qutschedule.ui.SettingsActivity;
import com.netessx.qutschedule.ui.TodayFragment;
import com.netessx.qutschedule.ui.TodoEditActivity;
import com.netessx.qutschedule.ui.WeekFragment;
import com.netessx.qutschedule.widget.ScheduleWidgetProvider;

/** 宿主：承载今日 / 课表 / 日程 / 我的四个页面，并统一处理编辑、删除与提醒同步。 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_TODAY = "today";
    private static final String TAG_SCHEDULE = "schedule";
    private static final String TAG_AGENDA = "agenda";
    private static final String TAG_MINE = "mine";

    private TodayFragment todayFragment;
    private WeekFragment weekFragment;
    private AgendaFragment agendaFragment;
    private MineFragment mineFragment;
    private String currentTag = TAG_TODAY;

    private final ActivityResultLauncher<Intent> pageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> refreshAll());

    private final ActivityResultLauncher<String> notificationLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.settings_notification_permission,
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 首次启动先进引导：不设内容视图就直接跳走，避免主界面闪一下
        if (!ScheduleStore.get(this).data().prefs.onboarded) {
            startActivity(com.netessx.qutschedule.ui.OnboardingActivity.newIntent(this));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        // edge-to-edge 强制开启，底部导航条与状态栏要让出高度
        com.netessx.qutschedule.ui.Insets.applySystemBars(findViewById(R.id.root_container));

        todayFragment = TodayFragment.newInstance();
        weekFragment = WeekFragment.newInstance();
        agendaFragment = AgendaFragment.newInstance();
        mineFragment = MineFragment.newInstance();

        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
                .add(R.id.fragment_container, mineFragment, TAG_MINE).hide(mineFragment)
                .add(R.id.fragment_container, agendaFragment, TAG_AGENDA).hide(agendaFragment)
                .add(R.id.fragment_container, weekFragment, TAG_SCHEDULE).hide(weekFragment)
                .add(R.id.fragment_container, todayFragment, TAG_TODAY)
                .commit();

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_schedule) {
                switchTo(TAG_SCHEDULE);
            } else if (id == R.id.nav_agenda) {
                switchTo(TAG_AGENDA);
            } else if (id == R.id.nav_mine) {
                switchTo(TAG_MINE);
            } else {
                switchTo(TAG_TODAY);
            }
            return true;
        });

        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void switchTo(String tag) {
        if (tag.equals(currentTag)) {
            return;
        }
        Fragment target = fm().findFragmentByTag(tag);
        Fragment from = fm().findFragmentByTag(currentTag);
        if (target == null) {
            return;
        }
        FragmentTransaction tx = fm().beginTransaction();
        if (from != null) {
            tx.hide(from);
        }
        tx.show(target).commit();
        currentTag = tag;
    }

    private FragmentManager fm() {
        return getSupportFragmentManager();
    }

    /** 打开课程编辑页；传入 null 表示新增。 */
    public void openEditor(Course course) {
        pageLauncher.launch(CourseEditActivity.intentFor(this, course));
    }

    /** 打开待办编辑页；传入 null 表示新增。 */
    public void openTodo(Todo todo) {
        pageLauncher.launch(TodoEditActivity.intentFor(this, todo));
    }

    public void openImport() {
        pageLauncher.launch(new Intent(this, ImportActivity.class));
    }

    public void openSettings() {
        pageLauncher.launch(new Intent(this, SettingsActivity.class));
    }

    /** 删除前确认，避免误触。 */
    public void confirmDelete(@NonNull final Course course) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.confirm_delete, course.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    ScheduleStore.get(this).removeCourse(course.id);
                    refreshAll();
                })
                .show();
    }

    /** 数据变化后刷新四个页面，并重建提醒与状态栏。 */
    public void refreshAll() {
        if (todayFragment != null) {
            todayFragment.refresh();
        }
        if (weekFragment != null) {
            weekFragment.refresh();
        }
        if (agendaFragment != null) {
            agendaFragment.refresh();
        }
        if (mineFragment != null) {
            mineFragment.refresh();
        }
        ReminderScheduler.sync(this);
        LiveUpdateService.refresh(this);
        ScheduleWidgetProvider.updateAll(this);
    }
}
