package com.netessx.qutschedule.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Profile;

/** 我的：按「课表 / 课程 / 偏好 / 关于」四组归类，明细功能都在二级页。 */
public class MineFragment extends Fragment {

    private TextView nickname;
    private TextView signature;
    private LinearLayout scheduleGroup;
    private LinearLayout courseGroup;
    private LinearLayout prefGroup;
    private LinearLayout aboutGroup;

    public static MineFragment newInstance() {
        return new MineFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mine, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        nickname = view.findViewById(R.id.tv_nickname);
        signature = view.findViewById(R.id.tv_signature);
        scheduleGroup = view.findViewById(R.id.group_schedule);
        courseGroup = view.findViewById(R.id.group_course);
        prefGroup = view.findViewById(R.id.group_pref);
        aboutGroup = view.findViewById(R.id.group_about);

        view.findViewById(R.id.card_profile).setOnClickListener(v -> open(ProfileActivity.class));
        buildEntries();
        refresh();
    }

    private void buildEntries() {
        scheduleGroup.removeAllViews();
        courseGroup.removeAllViews();
        prefGroup.removeAllViews();
        aboutGroup.removeAllViews();

        add(scheduleGroup, R.string.mine_import_pdf,
                R.string.mine_import_pdf_summary, ImportActivity.class);
        add(scheduleGroup, R.string.mine_semester,
                R.string.mine_semester_summary, SettingsActivity.class);
        add(scheduleGroup, R.string.mine_slots,
                R.string.mine_slots_summary, SlotSchemeActivity.class);
        add(scheduleGroup, R.string.mine_semester_list,
                R.string.mine_semester_list_summary, SemesterListActivity.class);

        add(courseGroup, R.string.mine_course_manage,
                R.string.mine_course_manage_summary, CourseManageActivity.class);

        add(prefGroup, R.string.mine_appearance,
                R.string.mine_appearance_summary, AppearanceActivity.class);
        add(prefGroup, R.string.mine_reminder,
                R.string.mine_reminder_summary, ReminderSettingsActivity.class);
        add(prefGroup, R.string.mine_holiday,
                R.string.mine_holiday_summary, HolidayActivity.class);
        add(prefGroup, R.string.mine_backup,
                R.string.mine_backup_summary, BackupActivity.class);

        add(aboutGroup, R.string.mine_about, R.string.mine_about_summary, AboutActivity.class);
    }

    private void add(LinearLayout parent, int titleRes, int summaryRes, final Class<?> activity) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_mine_entry, parent, false);
        ((TextView) row.findViewById(R.id.tv_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.tv_summary)).setText(summaryRes);
        row.setOnClickListener(v -> open(activity));
        parent.addView(row);
    }

    private void open(Class<?> activity) {
        Context ctx = requireContext();
        try {
            startActivity(new Intent(ctx, activity));
        } catch (RuntimeException e) {
            Toast.makeText(ctx, R.string.coming_soon, Toast.LENGTH_SHORT).show();
        }
    }

    /** 数据变化后由宿主调用。 */
    public void refresh() {
        if (!isAdded() || nickname == null) {
            return;
        }
        Profile profile = ScheduleStore.get(requireContext()).data().profile;
        nickname.setText(profile.nickname == null || profile.nickname.isEmpty()
                ? getString(R.string.mine_profile) : profile.nickname);
        signature.setText(profile.signature == null || profile.signature.isEmpty()
                ? profile.school : profile.signature);
    }
}
