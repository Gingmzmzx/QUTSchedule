package com.netessx.qutschedule.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** 登录后跳转「个人课表」的 URL 拼接规则。 */
public class WebImportUrlTest {

    private static final String HASH = "61c8de818623a3a77d1f9c950656ae9ea07fe33a0ecbc1221223a9b987b4a0fc";

    @Test
    public void homeWithQueryGoesToPortalEvent() {
        String url = "https://webvpn2.qut.edu.cn/https/webvpn" + HASH + "/?check_logged_in=1";
        assertEquals("https://webvpn2.qut.edu.cn/https/webvpn" + HASH + "/portal/event",
                WebImportActivity.portalEventUrl(url));
    }

    @Test
    public void alreadyOnPortalEventDoesNotNavigate() {
        String url = "https://webvpn2.qut.edu.cn/https/webvpn" + HASH + "/portal/event";
        assertNull(WebImportActivity.portalEventUrl(url));
    }

    @Test
    public void withoutWebvpnPrefixDoesNotNavigate() {
        assertNull(WebImportActivity.portalEventUrl("https://vpnportal.qut.edu.cn/"));
        assertNull(WebImportActivity.portalEventUrl(null));
    }

    @Test
    public void prefixKeepsTrailingSlash() {
        assertEquals("/https/webvpn" + HASH + "/",
                WebImportActivity.webvpnPrefix("https://webvpn2.qut.edu.cn/https/webvpn" + HASH + "/x?y=1"));
    }

    @Test
    public void prefixWithoutTrailingSlashIsIgnored() {
        assertNull(WebImportActivity.webvpnPrefix("https://webvpn2.qut.edu.cn/https/webvpn" + HASH));
    }
}
