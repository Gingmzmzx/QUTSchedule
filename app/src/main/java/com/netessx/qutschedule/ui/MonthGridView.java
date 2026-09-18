package com.netessx.qutschedule.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 月视图网格：固定 6 行 42 格，每格显示日期与当天日程的彩色圆点。 */
public class MonthGridView extends View {

    public interface OnDaySelectedListener {
        void onDaySelected(LocalDate date);
    }

    private static final int COLS = 7;
    private static final int ROWS = 6;
    private static final int CELLS = COLS * ROWS;
    private static final int MAX_DOTS = 4;
    private static final String[] HEADERS = {"一", "二", "三", "四", "五", "六", "日"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private LocalDate firstOfMonth;
    private LocalDate selected;
    private LocalDate today;
    @SuppressWarnings("unchecked")
    private List<Integer>[] dotColors = new List[CELLS];

    private OnDaySelectedListener listener;

    public MonthGridView(Context context) {
        this(context, null);
    }

    public MonthGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < CELLS; i++) {
            dotColors[i] = new ArrayList<>();
        }
    }

    public void setMonth(LocalDate firstOfMonth, LocalDate selected, LocalDate today,
                         List<Integer>[] dotColors) {
        this.firstOfMonth = firstOfMonth;
        this.selected = selected;
        this.today = today;
        if (dotColors != null) {
            this.dotColors = dotColors;
        }
        invalidate();
    }

    public void setOnDaySelectedListener(OnDaySelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int headerH = (int) (density * 22);
        int cellH = (int) (density * 42);
        setMeasuredDimension(width, headerH + cellH * ROWS);
    }

    private float headerHeight() {
        return density * 22;
    }

    private LocalDate dateAt(int index) {
        if (firstOfMonth == null) {
            return null;
        }
        return Dates.mondayOf(firstOfMonth).plusDays(index);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && firstOfMonth != null && listener != null) {
            float headerH = headerHeight();
            float cellW = getWidth() / (float) COLS;
            float cellH = (getHeight() - headerH) / (float) ROWS;
            int col = (int) (event.getX() / cellW);
            int row = (int) ((event.getY() - headerH) / cellH);
            if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                LocalDate date = dateAt(row * COLS + col);
                if (date != null) {
                    listener.onDaySelected(date);
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float headerH = headerHeight();
        float cellW = getWidth() / (float) COLS;
        float cellH = (getHeight() - headerH) / (float) ROWS;

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(11 * density);
        paint.setColor(getContext().getColor(R.color.text_secondary));
        for (int col = 0; col < COLS; col++) {
            canvas.drawText(HEADERS[col], cellW * col + cellW / 2f, headerH - 7 * density, paint);
        }

        if (firstOfMonth == null) {
            return;
        }

        for (int index = 0; index < CELLS; index++) {
            LocalDate date = dateAt(index);
            if (date == null) {
                continue;
            }
            int col = index % COLS;
            int row = index / COLS;
            float left = cellW * col;
            float top = headerH + cellH * row;
            rect.set(left + 2 * density, top + 2 * density,
                    left + cellW - 2 * density, top + cellH - 2 * density);

            if (date.equals(selected)) {
                paint.setColor(getContext().getColor(R.color.purple_500));
                paint.setAlpha(40);
                canvas.drawRoundRect(rect, 6 * density, 6 * density, paint);
                paint.setAlpha(255);
            }

            boolean inMonth = date.getMonthValue() == firstOfMonth.getMonthValue();
            paint.setTypeface(date.equals(today) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            paint.setTextSize(12 * density);
            paint.setColor(pickTextColor(date, inMonth));
            canvas.drawText(String.valueOf(date.getDayOfMonth()),
                    rect.centerX(), top + 18 * density, paint);

            List<Integer> colors = dotColors[index];
            if (colors != null && !colors.isEmpty()) {
                float dotR = 2.2f * density;
                float gap = 5.4f * density;
                int shown = Math.min(colors.size(), MAX_DOTS);
                float startX = rect.centerX() - (shown - 1) * gap / 2f;
                float cy = top + 31 * density;
                for (int d = 0; d < shown; d++) {
                    paint.setColor(colors.get(d));
                    canvas.drawCircle(startX + d * gap, cy, dotR, paint);
                }
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private int pickTextColor(LocalDate date, boolean inMonth) {
        if (date.equals(today)) {
            return getContext().getColor(R.color.purple_500);
        }
        if (!inMonth) {
            return getContext().getColor(R.color.grid_line);
        }
        return Dates.isWeekend(date)
                ? getContext().getColor(R.color.text_secondary)
                : getContext().getColor(R.color.black);
    }
}
