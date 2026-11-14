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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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

        add(scheduleGroup, R.drawable.ic_school, R.string.mine_import_web, WebImportActivity.class);
        add(scheduleGroup, R.drawable.ic_sync, R.string.mine_auto_refresh, AutoRefreshActivity.class);
        add(scheduleGroup, R.drawable.ic_file, R.string.mine_import_pdf, ImportActivity.class);
        add(scheduleGroup, R.drawable.ic_calendar, R.string.mine_semester, SettingsActivity.class);
        add(scheduleGroup, R.drawable.ic_schedule, R.string.mine_slots, SlotSchemeActivity.class);
        add(scheduleGroup, R.drawable.ic_list, R.string.mine_semester_list, SemesterListActivity.class);

        add(courseGroup, R.drawable.ic_menu_book, R.string.mine_course_manage, CourseManageActivity.class);

        add(prefGroup, R.drawable.ic_palette, R.string.mine_appearance, AppearanceActivity.class);
        add(prefGroup, R.drawable.ic_notifications, R.string.mine_reminder, ReminderSettingsActivity.class);
        add(prefGroup, R.drawable.ic_wb_sunny, R.string.mine_holiday, HolidayActivity.class);
        add(prefGroup, R.drawable.ic_cloud, R.string.mine_backup, BackupActivity.class);

        add(aboutGroup, R.drawable.ic_info, R.string.mine_about, AboutActivity.class);
    }

    /** 列表项只显示标题，不显示摘要；行与行之间用缩进的分隔线。 */
    private void add(LinearLayout parent, @DrawableRes int iconRes, int titleRes,
                     final Class<?> activity) {
        if (parent.getChildCount() > 0) {
            View divider = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            lp.setMarginStart(dp(64));
            lp.setMarginEnd(dp(16));
            divider.setLayoutParams(lp);
            divider.setBackgroundColor(ContextCompat.getColor(requireContext(),
                    R.color.colorOutlineVariant));
            divider.setAlpha(0.5f);
            parent.addView(divider);
        }
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_mine_entry, parent, false);
        ((ImageView) row.findViewById(R.id.iv_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.tv_title)).setText(titleRes);
        row.setOnClickListener(v -> open(activity));
        parent.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        String sig = profile.signature == null || profile.signature.isEmpty()
                ? profile.school : profile.signature;
        signature.setText(sig == null || sig.isEmpty()
                ? getString(R.string.mine_profile_hint) : sig);
    }
}
