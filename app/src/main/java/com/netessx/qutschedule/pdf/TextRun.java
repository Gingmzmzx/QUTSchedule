package com.netessx.qutschedule.pdf;

/** 一段带坐标的文本，坐标取自 PDF 文本矩阵（Tj 前的 Tm）。 */
public class TextRun {

    public final float x;
    public final float y;
    public final String text;

    public TextRun(float x, float y, String text) {
        this.x = x;
        this.y = y;
        this.text = text;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ") " + text;
    }
}
