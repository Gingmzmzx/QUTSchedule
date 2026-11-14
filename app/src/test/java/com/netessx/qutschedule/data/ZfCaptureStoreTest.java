package com.netessx.qutschedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 抓取结果落盘：重写 meta 时不能把兜底 Cookie 一起冲掉。 */
public class ZfCaptureStoreTest {

    private static final String URL = "https://webvpn2.qut.edu.cn/https/webvpnabc/portal/api/v2/datacenter/get/xskb";
    private static final String COOKIE = "ENSSESSIONID=abc123; clientInfo=xyz";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private ZfCaptureStore newStore() throws Exception {
        return new ZfCaptureStore(folder.newFolder());
    }

    @Test
    public void resavingCaptureKeepsFallbackCookie() throws Exception {
        ZfCaptureStore store = newStore();
        store.save(URL, "GET", "{\"a\":1}", 200, "page");
        store.saveCookie(URL, COOKIE);
        long cookieAt = store.latestMeta().optLong("cookieAt");
        assertTrue(cookieAt > 0);

        // 自动刷新成功会再 save 一次；兜底 Cookie 必须继承下来
        store.save(URL, "GET", "{\"a\":2}", 200, "auto");

        JSONObject meta = store.latestMeta();
        assertNotNull(meta);
        assertEquals(COOKIE, meta.optString("cookie"));
        assertEquals(cookieAt, meta.optLong("cookieAt"));
        assertEquals("auto", meta.optString("source"));
        assertEquals(7, meta.optInt("chars")); // {"a":2} 共 7 个字符
    }

    @Test
    public void firstSaveOnEmptyDirDoesNotThrow() throws Exception {
        ZfCaptureStore store = newStore();
        store.save(URL, "GET", "{}", 200, "page");
        JSONObject meta = store.latestMeta();
        assertNotNull(meta);
        assertEquals(URL, meta.optString("url"));
        assertTrue(meta.optString("cookie").isEmpty());
    }

    @Test
    public void corruptMetaDropsCookieButKeepsSaving() throws Exception {
        File dir = folder.newFolder();
        Files.write(new File(dir, "xskb.meta.json").toPath(),
                "{not json".getBytes(StandardCharsets.UTF_8));
        ZfCaptureStore store = new ZfCaptureStore(dir);

        store.save(URL, "GET", "{}", 200, "page");

        JSONObject meta = store.latestMeta();
        assertNotNull(meta);
        assertEquals(URL, meta.optString("url"));
        assertTrue(meta.optString("cookie").isEmpty());
    }

    @Test
    public void emptyCookieDoesNotOverwriteExisting() throws Exception {
        ZfCaptureStore store = newStore();
        store.save(URL, "GET", "{}", 200, "page");
        store.saveCookie(URL, COOKIE);

        store.saveCookie(URL, null);
        store.saveCookie(URL, "   ");

        assertEquals(COOKIE, store.latestMeta().optString("cookie"));
    }
}
