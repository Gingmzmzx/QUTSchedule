package com.netessx.qutschedule.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 可缩放的图片：双指捏合缩放、双击在「适应屏幕 / 2.5 倍」之间切换、放大后可拖动平移。
 *
 * <p>用 {@link ScaleType#MATRIX} 自己管矩阵，因为要按当前缩放比例重新贴合边界。
 * 单击（等双击判定超时后确认）会回调 {@link #setOnDismissTapListener}，交给调用方关闭页面。
 */
public class ZoomableImageView extends AppCompatImageView {

    private static final float DOUBLE_TAP_FACTOR = 2.5f;

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector tapDetector;

    private float fitScale = 1f;
    private float maxScale = 5f;
    private float lastX;
    private float lastY;
    private boolean dragging;

    private Runnable dismissTapListener;

    public ZoomableImageView(Context context) {
        this(context, null);
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        applyScale(detector.getScaleFactor(), detector.getFocusX(),
                                detector.getFocusY());
                        return true;
                    }
                });

        tapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleZoom(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (dismissTapListener != null) {
                    dismissTapListener.run();
                }
                return true;
            }
        });
    }

    public void setOnDismissTapListener(Runnable listener) {
        this.dismissTapListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetToFit();
    }

    /** 把图片按比例居中铺满可用区域。 */
    public void resetToFit() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float width = drawable.getIntrinsicWidth();
        float height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float scale = Math.min(getWidth() / width, getHeight() / height);
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate((getWidth() - width * scale) / 2f,
                (getHeight() - height * scale) / 2f);
        fitScale = scale;
        maxScale = scale * 5f;
        setImageMatrix(matrix);
    }

    private void applyScale(float factor, float focusX, float focusY) {
        matrix.getValues(values);
        float current = values[Matrix.MSCALE_X];
        float target = Math.max(fitScale * 0.8f, Math.min(maxScale, current * factor));
        float actual = target / current;
        matrix.postScale(actual, actual, focusX, focusY);
        clamp();
        setImageMatrix(matrix);
    }

    private void toggleZoom(float focusX, float focusY) {
        matrix.getValues(values);
        float factor = values[Matrix.MSCALE_X] > fitScale * 1.2f
                ? fitScale / values[Matrix.MSCALE_X]
                : DOUBLE_TAP_FACTOR;
        matrix.postScale(factor, factor, focusX, focusY);
        clamp();
        setImageMatrix(matrix);
    }

    /** 放大后把图片限制在可视区域内，缩到比屏幕小时居中。 */
    private void clamp() {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        float width = drawable.getIntrinsicWidth() * scale;
        float height = drawable.getIntrinsicHeight() * scale;
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        float fixedX = width <= getWidth()
                ? (getWidth() - width) / 2f
                : Math.max(getWidth() - width, Math.min(0f, transX));
        float fixedY = height <= getHeight()
                ? (getHeight() - height) / 2f
                : Math.max(getHeight() - height, Math.min(0f, transY));

        matrix.postTranslate(fixedX - transX, fixedY - transY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        tapDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    lastX = event.getX();
                    lastY = event.getY();
                    matrix.postTranslate(dx, dy);
                    clamp();
                    setImageMatrix(matrix);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
            default:
                break;
        }
        return true;
    }
}
