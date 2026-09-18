package com.netessx.qutschedule.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * 极简 PDF 文本提取器。
 *
 * <p>教务系统导出的课表结构固定：内容流用 FlateDecode 压缩，文字以 {@code Tm} 定位后由 {@code Tj}
 * 输出，字体统一为 UniGB-UCS2-H，因此字符串本身就是 UTF-16BE。这里只走这一条路径，不引入第三方
 * PDF 库。
 */
public final class PdfTextExtractor {

    /** 防止畸形文件把内存吃满。 */
    private static final int MAX_STREAMS = 64;
    private static final int MAX_CONTENT_BYTES = 8 * 1024 * 1024;

    private static final Object ARRAY_MARKER = new Object();

    private PdfTextExtractor() {
    }

    public static List<TextRun> extract(byte[] pdf) throws IOException {
        if (pdf == null || pdf.length == 0) {
            throw new IOException("文件为空");
        }
        List<TextRun> runs = new ArrayList<>();
        for (byte[] raw : findStreams(pdf)) {
            byte[] content = inflate(raw);
            if (content == null || content.length == 0) {
                continue;
            }
            String text = new String(content, StandardCharsets.ISO_8859_1);
            if (text.indexOf("Tj") < 0 && text.indexOf("TJ") < 0) {
                continue;
            }
            parseContent(text, runs);
        }
        return runs;
    }

    /** 扫描 stream ... endstream 之间的原始数据。 */
    private static List<byte[]> findStreams(byte[] pdf) {
        List<byte[]> out = new ArrayList<>();
        int i = 0;
        while (i < pdf.length && out.size() < MAX_STREAMS) {
            int marker = indexOf(pdf, "stream", i);
            if (marker < 0) {
                break;
            }
            int start = marker + "stream".length();
            if (start < pdf.length && pdf[start] == '\r') {
                start++;
            }
            if (start < pdf.length && pdf[start] == '\n') {
                start++;
            }
            int end = indexOf(pdf, "endstream", start);
            if (end < 0) {
                break;
            }
            int stop = end;
            while (stop > start && (pdf[stop - 1] == '\r' || pdf[stop - 1] == '\n')) {
                stop--;
            }
            byte[] chunk = new byte[stop - start];
            System.arraycopy(pdf, start, chunk, 0, chunk.length);
            out.add(chunk);
            i = end + "endstream".length();
        }
        return out;
    }

    private static int indexOf(byte[] data, String needle, int from) {
        byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = Math.max(0, from); i <= data.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (data[i + j] != n[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** 先按 zlib 解压，失败再按裸 deflate 试一次。 */
    private static byte[] inflate(byte[] raw) {
        byte[] zlib = inflateWith(raw, false);
        return zlib != null ? zlib : inflateWith(raw, true);
    }

    private static byte[] inflateWith(byte[] raw, boolean nowrap) {
        Inflater inflater = new Inflater(nowrap);
        try {
            inflater.setInput(raw);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length * 3));
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                out.write(buffer, 0, n);
                if (out.size() > MAX_CONTENT_BYTES) {
                    return null;
                }
            }
            return out.size() == 0 ? null : out.toByteArray();
        } catch (DataFormatException | RuntimeException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static void parseContent(String s, List<TextRun> out) {
        int i = 0;
        int n = s.length();
        List<Object> stack = new ArrayList<>();
        float tx = 0f;
        float ty = 0f;

        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '%') {
                while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (c == '(') {
                StringBuilder sb = new StringBuilder();
                int depth = 1;
                i++;
                while (i < n && depth > 0) {
                    char ch = s.charAt(i);
                    if (ch == '\\') {
                        i++;
                        if (i >= n) {
                            break;
                        }
                        char e = s.charAt(i);
                        switch (e) {
                            case 'n': sb.append('\n'); i++; break;
                            case 'r': sb.append('\r'); i++; break;
                            case 't': sb.append('\t'); i++; break;
                            case 'b': sb.append('\b'); i++; break;
                            case 'f': sb.append('\f'); i++; break;
                            case '(': sb.append('('); i++; break;
                            case ')': sb.append(')'); i++; break;
                            case '\\': sb.append('\\'); i++; break;
                            default:
                                if (e >= '0' && e <= '7') {
                                    int value = 0;
                                    int digits = 0;
                                    while (digits < 3 && i < n && s.charAt(i) >= '0' && s.charAt(i) <= '7') {
                                        value = value * 8 + (s.charAt(i) - '0');
                                        i++;
                                        digits++;
                                    }
                                    sb.append((char) (value & 0xFF));
                                } else {
                                    sb.append(e);
                                    i++;
                                }
                                break;
                        }
                        continue;
                    }
                    if (ch == '(') {
                        depth++;
                        sb.append(ch);
                        i++;
                        continue;
                    }
                    if (ch == ')') {
                        depth--;
                        if (depth > 0) {
                            sb.append(ch);
                        }
                        i++;
                        continue;
                    }
                    sb.append(ch);
                    i++;
                }
                stack.add(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                continue;
            }
            if (c == '<' && i + 1 < n && s.charAt(i + 1) == '<') {
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < n && s.charAt(i + 1) == '>') {
                i += 2;
                continue;
            }
            if (c == '<') {
                int close = s.indexOf('>', i);
                if (close < 0) {
                    break;
                }
                stack.add(hexToBytes(s.substring(i + 1, close)));
                i = close + 1;
                continue;
            }
            if (c == '[') {
                stack.add(ARRAY_MARKER);
                i++;
                continue;
            }
            if (c == ']') {
                List<Object> items = new ArrayList<>();
                while (!stack.isEmpty()) {
                    Object top = stack.remove(stack.size() - 1);
                    if (top == ARRAY_MARKER) {
                        break;
                    }
                    items.add(0, top);
                }
                stack.add(items);
                i++;
                continue;
            }
            if (c == '/') {
                i++;
                while (i < n && !isDelimiter(s.charAt(i))) {
                    i++;
                }
                continue;
            }
            if (c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9')) {
                int start = i;
                i++;
                while (i < n && (s.charAt(i) == '.' || s.charAt(i) == '-' || s.charAt(i) == '+'
                        || (s.charAt(i) >= '0' && s.charAt(i) <= '9'))) {
                    i++;
                }
                try {
                    stack.add(Float.parseFloat(s.substring(start, i)));
                } catch (NumberFormatException e) {
                    stack.add(0f);
                }
                continue;
            }
            if (Character.isLetter(c) || c == '\'' || c == '"') {
                int start = i;
                i++;
                while (i < n && !isDelimiter(s.charAt(i))) {
                    i++;
                }
                String op = s.substring(start, i);
                handleOperator(op, stack, out, tx, ty);
                float[] pos = applyOperator(op, stack, tx, ty);
                tx = pos[0];
                ty = pos[1];
                continue;
            }
            i++;
        }
    }

    private static boolean isDelimiter(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' || c == '<' || c == '>'
                || c == '[' || c == ']' || c == '/' || c == '%';
    }

    private static float[] applyOperator(String op, List<Object> stack, float tx, float ty) {
        if ("Tm".equals(op) && stack.size() >= 6) {
            float f = popFloat(stack);
            float e = popFloat(stack);
            popFloat(stack);
            popFloat(stack);
            popFloat(stack);
            popFloat(stack);
            return new float[]{e, f};
        }
        if (("Td".equals(op) || "TD".equals(op)) && stack.size() >= 2) {
            float dy = popFloat(stack);
            float dx = popFloat(stack);
            return new float[]{tx + dx, ty + dy};
        }
        return new float[]{tx, ty};
    }

    private static void handleOperator(String op, List<Object> stack, List<TextRun> out,
                                       float tx, float ty) {
        if ("Tj".equals(op) && !stack.isEmpty()) {
            Object top = stack.remove(stack.size() - 1);
            if (top instanceof byte[]) {
                out.add(new TextRun(tx, ty, decode((byte[]) top)));
            }
            return;
        }
        if ("TJ".equals(op) && !stack.isEmpty()) {
            Object top = stack.remove(stack.size() - 1);
            if (top instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object item : (List<?>) top) {
                    if (item instanceof byte[]) {
                        sb.append(decode((byte[]) item));
                    }
                }
                if (sb.length() > 0) {
                    out.add(new TextRun(tx, ty, sb.toString()));
                }
            }
            return;
        }
        if (("'".equals(op) || "\"".equals(op)) && !stack.isEmpty()) {
            Object top = stack.remove(stack.size() - 1);
            if (top instanceof byte[]) {
                out.add(new TextRun(tx, ty, decode((byte[]) top)));
            }
        }
    }

    private static float popFloat(List<Object> stack) {
        if (stack.isEmpty()) {
            return 0f;
        }
        Object top = stack.remove(stack.size() - 1);
        return top instanceof Number ? ((Number) top).floatValue() : 0f;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            out[i] = (byte) (((hi < 0 ? 0 : hi) << 4) | (lo < 0 ? 0 : lo));
        }
        return out;
    }

    /** 字体编码为 UniGB-UCS2-H，字节即 UTF-16BE。 */
    private static String decode(byte[] bytes) {
        if (bytes.length >= 2 && bytes.length % 2 == 0) {
            String s = new String(bytes, StandardCharsets.UTF_16BE);
            if (!s.isEmpty() && s.charAt(0) == '﻿') {
                s = s.substring(1);
            }
            return s;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
