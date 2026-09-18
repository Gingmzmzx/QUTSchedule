package com.netessx.qutschedule.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表排版的三个小工具：字号自适应、按分隔符优先换行、按宽度截断。
 *
 * <p>换行不直接硬切，而是优先落在 {@code @ （ ( 【 , ，} 这些分隔符前，
 * 这样 {@code A-302@东区} 会断成「A-302 / @东区」，不会出现「A-3 / 02」。
 */
public final class TextFit {

    /** 零宽空格，标记一个合法的换行点。 */
    private static final char BREAK = '​';
    private static final String BREAK_BEFORE = "@（(【,，";
    private static final String BREAK_AFTER = "）)】";

    private TextFit() {
    }

    /** 在地点的分隔符前插入换行机会。 */
    public static String wrapPlace(String place) {
        if (place == null || place.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(place.length() + 8);
        for (int i = 0; i < place.length(); i++) {
            char c = place.charAt(i);
            if (i > 0 && BREAK_BEFORE.indexOf(c) >= 0) {
                sb.append(BREAK);
            }
            sb.append(c);
            if (BREAK_AFTER.indexOf(c) >= 0 && i < place.length() - 1) {
                sb.append(BREAK);
            }
        }
        return sb.toString();
    }

    /** 在给定宽度与行数内，为文本挑一个尽量大的字号。 */
    public static float fitSize(Paint paint, String text, float maxWidth,
                                float maxSize, float minSize, int maxLines) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return maxSize;
        }
        float size = maxSize;
        while (size > minSize) {
            paint.setTextSize(size);
            if (measureLines(paint, text, maxWidth) <= maxLines) {
                break;
            }
            size -= 0.5f;
        }
        paint.setTextSize(size);
        return size;
    }

    /** 文本按当前字号需要几行。 */
    public static int measureLines(Paint paint, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return 1;
        }
        return layout(paint, text, maxWidth).size();
    }

    /**
     * 按宽度折行绘制，最多 {@code maxLines} 行，最后一行超出时截断并加省略号。
     *
     * @return 下一行的基线纵坐标
     */
    public static float drawWrapped(Canvas canvas, Paint paint, String text, float x,
                                    float baseline, float maxWidth, float lineHeight, int maxLines) {
        if (text == null || text.isEmpty() || maxWidth <= 0 || maxLines <= 0) {
            return baseline;
        }
        List<String> lines = layout(paint, text, maxWidth);
        float y = baseline;
        int drawn = Math.min(lines.size(), maxLines);
        for (int i = 0; i < drawn; i++) {
            String line = lines.get(i);
            if (i == drawn - 1 && i < lines.size() - 1) {
                line = ellipsize(paint, line + lines.get(i + 1), maxWidth);
            }
            canvas.drawText(line, x, y, paint);
            y += lineHeight;
        }
        return y;
    }

    public static void drawCentered(Canvas canvas, Paint paint, String text, float cx, float cy,
                                    float size, int color) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(text, cx, cy, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    public static String ellipsize(Paint paint, String text, float maxWidth) {
        String result = text;
        while (result.length() > 1 && paint.measureText(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    /** 折行结果，每项是一行文本。 */
    public static List<String> layout(Paint paint, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String piece : pieces(paint, text, maxWidth)) {
            if (piece == null) {
                lines.add(line.toString());
                line.setLength(0);
                continue;
            }
            if (line.length() > 0 && paint.measureText(line.toString() + piece) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(piece);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * 拆成可排版的片段：{@code null} 表示强制换行，其余片段应尽量整体落在一行；
     * 单段本身就超宽时硬切。
     */
    private static List<String> pieces(Paint paint, String text, float maxWidth) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            boolean atEnd = i == text.length();
            if (!atEnd && text.charAt(i) != BREAK) {
                continue;
            }
            if (i > start) {
                appendPiece(out, paint, text.substring(start, i), maxWidth);
            } else if (!atEnd) {
                out.add(null);
            }
            start = i + 1;
        }
        return out;
    }

    private static void appendPiece(List<String> out, Paint paint, String piece, float maxWidth) {
        if (paint.measureText(piece) <= maxWidth) {
            out.add(piece);
            return;
        }
        String rest = piece;
        while (!rest.isEmpty()) {
            int count = paint.breakText(rest, true, maxWidth, null);
            if (count <= 0) {
                count = 1;
            }
            out.add(rest.substring(0, count));
            rest = rest.substring(count);
        }
    }
}
