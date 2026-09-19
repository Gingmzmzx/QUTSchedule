package com.netessx.qutschedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.TimeScheme;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.Dates;
import com.netessx.qutschedule.util.TextFit;
import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 周视图网格：7 列 × N 个节次段。
 *
 * <p>排版上做了三件事：字号随块的可用宽度自适应，避免窄块被裁切；地点在 @、-、括号等分隔符处
 * 优先换行；同名课程统一颜色、不同课程优先分配未占用颜色。
 */
public class WeekGridView extends View {

    public interface OnCourseListener {
        void onCourseClick(Course course);

        void onCourseLongClick(Course course, LocalDate date);
    }

    /** 长按没有课的格子：用来在这一天临时加一节课。 */
    public interface OnEmptyLongClickListener {
        void onEmptyLongClick(LocalDate date);
    }

    public interface OnWeekChangeListener {
        void onWeekChange(int deltaWeeks);
    }

    public interface OnTitleClickListener {
        void onTitleClick();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final List<Course> hitCourses = new ArrayList<>();
    private final List<RectF> hitRects = new ArrayList<>();
    /** 与 hitCourses 一一对应，长按时要知道这块课在哪一天，临时调整以它为基准。 */
    private final List<LocalDate> hitDates = new ArrayList<>();

    private Semester semester;
    private TimeScheme scheme;
    private List<Course>[] grid;
    private LocalDate monday;
    private LocalDate today;
    private boolean todayInWeek;
    private Prefs prefs = new Prefs();

    private OnCourseListener courseListener;
    private OnWeekChangeListener weekChangeListener;
    private OnTitleClickListener titleListener;
    private OnEmptyLongClickListener emptyLongClickListener;
    private final GestureDetector detector;
    private final float density;
    /** 由 onMeasure 按「格子高度」偏好算好，onDraw 直接用。 */
    private float rowHeight;

    public WeekGridView(Context context) {
        this(context, null);
    }

    public WeekGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, density * 0.5f));
        linePaint.setColor(context.getColor(R.color.grid_line));
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || weekChangeListener == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > density * 60 && Math.abs(dx) > Math.abs(dy)) {
                    weekChangeListener.onWeekChange(dx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Course course = courseAt(e.getX(), e.getY());
                if (course != null && courseListener != null) {
                    courseListener.onCourseLongClick(course, dateOf(course));
                } else if (course == null && titleListener != null && e.getY() < headerHeight()) {
                    titleListener.onTitleClick();
                } else if (course == null && emptyLongClickListener != null) {
                    LocalDate date = dayAt(e.getX());
                    if (date != null) {
                        emptyLongClickListener.onEmptyLongClick(date);
                    }
                }
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Course course = courseAt(e.getX(), e.getY());
                if (course != null && courseListener != null) {
                    courseListener.onCourseClick(course);
                    return true;
                }
                if (e.getY() < headerHeight() && titleListener != null) {
                    titleListener.onTitleClick();
                    return true;
                }
                return false;
            }
        });
    }

    public void setPrefs(Prefs prefs) {
        if (prefs != null) {
            this.prefs = prefs;
        }
        invalidate();
    }

    public void setWeek(Semester semester, TimeScheme scheme, int weekNumber, LocalDate monday,
                        List<Course>[] grid, LocalDate today) {
        this.semester = semester;
        this.scheme = scheme;
        this.monday = monday;
        this.today = today;
        this.todayInWeek = monday != null && today != null
                && !today.isBefore(monday) && !today.isAfter(monday.plusDays(daysShown() - 1));
        if (grid != null) {
            this.grid = grid;
        }
        invalidate();
    }

    public void setOnCourseListener(OnCourseListener listener) {
        this.courseListener = listener;
    }

    public void setOnWeekChangeListener(OnWeekChangeListener listener) {
        this.weekChangeListener = listener;
    }

    public void setOnTitleClickListener(OnTitleClickListener listener) {
        this.titleListener = listener;
    }

    public void setOnEmptyLongClickListener(OnEmptyLongClickListener listener) {
        this.emptyLongClickListener = listener;
    }

    private int daysShown() {
        return prefs.showWeekend || weekendHasCourses() ? 7 : 5;
    }

    /** 周末有课时（例如调休补课）必须显示，否则整列会被「隐藏周末」吞掉。 */
    private boolean weekendHasCourses() {
        return grid != null && grid.length >= 7
                && ((grid[5] != null && !grid[5].isEmpty())
                || (grid[6] != null && !grid[6].isEmpty()));
    }

    private float headerHeight() {
        return density * 42;
    }

    private float timeColumnWidth() {
        // 有楼层错峰时时间列要放下「4F+ 10:20」这种两行标注
        return density * (hasFloorSplit() ? 42 : 34);
    }

    private boolean hasFloorSplit() {
        if (scheme == null) {
            return false;
        }
        for (TimeScheme.Slot slot : scheme.slots) {
            if (slot != null && slot.hasFloorSplit()) {
                return true;
            }
        }
        return false;
    }

    private int bands() {
        return scheme == null ? TimeSlots.BANDS : Math.max(1, scheme.count());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : Math.round(density * 520);
        float gridHeight = Math.max(density * 120,
                available - density * 4 - headerHeight());
        rowHeight = prefs.gridHeightScale * gridHeight / bands();
        int content = Math.round(density * 4 + headerHeight() + rowHeight * bands());
        setMeasuredDimension(width, Math.max(available, content));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return detector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private Course courseAt(float x, float y) {
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            if (hitRects.get(i).contains(x, y)) {
                return hitCourses.get(i);
            }
        }
        return null;
    }

    /** 某个课程块画在哪一天；绘制时记的，比按坐标反推可靠。 */
    private LocalDate dateOf(Course course) {
        int index = hitCourses.lastIndexOf(course);
        return index >= 0 && index < hitDates.size() ? hitDates.get(index) : null;
    }

    /** 某一列对应的日期；点在时间列或网格外返回 null。 */
    private LocalDate dayAt(float x) {
        if (monday == null) {
            return null;
        }
        int days = daysShown();
        float gridLeft = density * 2 + timeColumnWidth();
        float colW = (getWidth() - density * 2 - gridLeft) / days;
        if (colW <= 0) {
            return null;
        }
        int day = (int) ((x - gridLeft) / colW);
        return day < 0 || day >= days ? null : monday.plusDays(day);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        hitRects.clear();
        hitCourses.clear();
        hitDates.clear();

        int days = daysShown();
        float pad = density * 2;
        float timeW = timeColumnWidth();
        float headerH = headerHeight();
        float gridLeft = pad + timeW;
        float gridTop = pad + headerH;
        float gridRight = getWidth() - pad;
        float gridBottom = gridTop + rowHeight * bands();
        float colW = (gridRight - gridLeft) / days;

        if (semester == null || !semester.isConfigured()) {
            TextFit.drawCentered(canvas, paint, getContext().getString(R.string.no_schedule_yet),
                    getWidth() / 2f, getHeight() / 2f, 14 * density,
                    getContext().getColor(R.color.text_secondary));
            return;
        }
        drawHeader(canvas, gridLeft, pad, colW, headerH, gridTop);
        drawBackground(canvas, gridLeft, gridTop, gridRight, gridBottom, colW);
        drawGridLines(canvas, gridLeft, gridTop, gridRight, gridBottom, colW);
        drawCourses(canvas, gridLeft, gridTop, colW, gridBottom - gridTop, days);
    }

    private void drawHeader(Canvas canvas, float gridLeft, float top, float colW,
                            float headerH, float gridTop) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < daysShown(); i++) {
            float cx = gridLeft + colW * i + colW / 2f;
            LocalDate date = monday == null ? null : monday.plusDays(i);
            boolean isToday = date != null && date.equals(today);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(11 * density);
            paint.setColor(isToday
                    ? getContext().getColor(R.color.purple_500)
                    : getContext().getColor(R.color.text_secondary));
            canvas.drawText(date == null ? "" : "周" + Dates.weekName(date), cx, top + 15 * density, paint);

            if (date != null) {
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(10 * density);
                canvas.drawText(String.valueOf(date.getDayOfMonth()), cx, top + 29 * density, paint);
            }
        }

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(8.5f * density);
        paint.setColor(getContext().getColor(R.color.text_secondary));
        int bands = bands();
        float rowH = rowHeight;
        float cx = (gridLeft - 4 * density) / 2f;
        for (int band = 0; band < bands; band++) {
            float cy = gridTop + rowH * band + 11 * density;
            canvas.drawText(scheme == null ? "" : scheme.labelOf(band * 2 + 1), cx, cy, paint);
            boolean hasSlot = scheme != null && band < scheme.slots.size();
            if (!hasSlot) {
                continue;
            }
            TimeScheme.Slot slot = scheme.slots.get(band);
            paint.setTextSize(7.5f * density);
            canvas.drawText(slot.start, cx, cy + 9 * density, paint);
            if (slot.hasFloorSplit()) {
                // 这一大节按楼层错峰，第二行补上高层那套时间
                paint.setTextSize(6.5f * density);
                canvas.drawText(slot.floorFrom + "F+ " + slot.floorStart,
                        cx, cy + 18 * density, paint);
            }
            paint.setTextSize(8.5f * density);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBackground(Canvas canvas, float left, float top, float right,
                                float bottom, float colW) {
        if (todayInWeek && prefs.showWeekend) {
            long index = today.toEpochDay() - monday.toEpochDay();
            if (index >= 0 && index < daysShown()) {
                shadePaint.setColor(getContext().getColor(R.color.today_column));
                canvas.drawRect(left + colW * index, top, left + colW * (index + 1), bottom, shadePaint);
            }
        }
    }

    private void drawGridLines(Canvas canvas, float left, float top, float right,
                               float bottom, float colW) {
        int bands = scheme == null ? 1 : scheme.count();
        float rowH = (bottom - top) / bands;
        for (int i = 0; i <= daysShown(); i++) {
            float x = left + colW * i;
            canvas.drawLine(x, top, x, bottom, linePaint);
        }
        for (int b = 0; b <= bands; b++) {
            float y = top + rowH * b;
            canvas.drawLine(left, y, right, y, linePaint);
        }
    }

    private void drawCourses(Canvas canvas, float left, float top, float colW,
                             float height, int days) {
        int bands = scheme == null ? 1 : scheme.count();
        float rowH = height / bands;
        float half = rowH / 2f;
        int alpha = Math.round(255f * prefs.gridAlpha / 100f);
        // 关掉「显示非本周课程」后，翻到别的周时整屏淡化，一眼能看出这不是本周
        boolean viewingCurrentWeek = monday != null && today != null
                && Dates.mondayOf(today).equals(monday);
        int dimAlpha = prefs.showNonCurrentWeek || viewingCurrentWeek ? alpha : alpha / 3;

        for (int day = 0; day < days; day++) {
            List<Course> courses = grid == null || day >= grid.length ? null : grid[day];
            if (courses == null) {
                continue;
            }
            for (Course course : courses) {
                int startBand = Math.min(scheme == null ? 0 : scheme.bandOf(course.startSlot), bands - 1);
                int endBand = Math.min(scheme == null ? 0 : scheme.bandOf(course.endSlot), bands - 1);
                float blockTop = top + startBand * rowH;
                float blockBottom = top + (endBand + 1) * rowH;
                if (course.startSlot % 2 == 0) {
                    blockTop += half;
                }
                if (course.endSlot % 2 == 1) {
                    blockBottom -= half;
                }
                float gap = prefs.gridGap * density;
                // 上下也用设置里的间距，否则相邻两节课会严丝合缝地贴在一起
                blockTop += gap;
                blockBottom -= gap;
                if (blockBottom - blockTop < 16 * density) {
                    blockBottom = blockTop + 16 * density;
                }
                float blockLeft = left + colW * day + gap;
                float blockRight = blockLeft + colW - gap * 2;

                rect.set(blockLeft, blockTop, blockRight, blockBottom);
                paint.setColor(ColorPalette.colorOf(getContext(), course));
                paint.setAlpha(dimAlpha);
                float corner = prefs.gridCorner * density;
                canvas.drawRoundRect(rect, corner, corner, paint);
                paint.setAlpha(255);

                hitRects.add(new RectF(rect));
                hitCourses.add(course);
                hitDates.add(monday == null ? null : monday.plusDays(day));

                drawBlockText(canvas, course, rect);
            }
        }
    }

    /** 名称 > 地点 > 教师，块越高给的信息越多。 */
    private void drawBlockText(Canvas canvas, Course course, RectF box) {
        float height = box.height();
        if (height < 16 * density) {
            return;
        }
        float padding = 3 * density;
        float maxWidth = box.width() - padding * 2;
        if (maxWidth <= 0) {
            return;
        }
        paint.setColor(getContext().getColor(R.color.block_text));

        float nameSize = TextFit.fitSize(paint, course.name, maxWidth, 11 * density, 7.5f * density, 2);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(nameSize);
        int lines = height > 62 * density ? 3 : (height > 34 * density ? 2 : 1);
        float y = TextFit.drawWrapped(canvas, paint, course.name, box.left + padding,
                box.top + nameSize + 1.5f * density, maxWidth, nameSize * 1.06f, lines);

        if (height > 62 * density && course.location != null && !course.location.isEmpty()) {
            paint.setTypeface(Typeface.DEFAULT);
            float placeSize = Math.min(9 * density, nameSize - 1f);
            paint.setTextSize(placeSize);
            TextFit.drawWrapped(canvas, paint, TextFit.wrapPlace(course.location),
                    box.left + padding, y + placeSize * 0.4f, maxWidth, placeSize * 1.05f, 2);
        }
    }
}
