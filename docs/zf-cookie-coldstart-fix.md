# 修复说明：后台刷新误报「登录已过期」

- 适用：在后台任务（JobService / BroadcastReceiver / 后台线程）里用 WebView 的 `CookieManager` 重放登录后请求的场景
- 影响版本：QUTSchedule v1.7.0 Beta1
- 修复版本：QUTSchedule v1.7.0 Beta2（versionCode 26111407）
- 参考实现：`app/src/main/java/com/netessx/qutschedule/auto/ZfRefresher.java`、`ZfKeepAliveJobService.java`、`data/ZfCaptureStore.java`

---

## 1. 现象

- 用户登录教务系统后，自动刷新正常；但过一段时间（进程被系统回收、重新打开、或后台任务在冷进程里执行后），点「立即更新」或定时刷新会提示「登录已过期」。
- 此时重新打开应用内的 WebView 页面，同一账号依然是登录状态，手动再刷新又能成功。
- 表现为随机复现，且越接近「进程刚被拉起」越容易触发。

## 2. 根因

调用链：

1. 后台刷新需要带登录态，代码从 WebView 的 Cookie 仓读取：
   `CookieManager.getInstance().getCookie(url)`
2. 进程刚启动时，WebView 的 provider 尚未初始化，**第一次 `getCookie` 返回 `null` 或空串**；初始化是异步的，稍后再读就能拿到。
3. 代码把空结果当成「WebView 没有 Cookie」，回退到持久化的兜底 Cookie（上次成功刷新时保存的那份）。
4. 兜底 Cookie 往往已经过期，或被服务端轮换过，请求被 WebVPN 302 到统一身份认证登录页。
5. 判定逻辑把「请求返回 302 / 非 JSON」直接当成「登录失效」，于是：
   - 弹出「教务系统登录已过期」通知；
   - 把自动刷新标记为暂停。

所以本质上不是会话过期，而是**冷启动时读 Cookie 的时机问题 + 错误的判定依据**。

## 3. 修复

### 3.1 读取前预热 WebView，并重试

在主线程创建一个一次性 `WebView`，强制 provider 初始化，然后重试读取：

```java
static String cookieFor(Context app, String url) {
    String cookie = readCookie(url);
    if (cookie != null && !cookie.isEmpty()) {
        return cookie;
    }
    warmUpWebView(app);
    for (int i = 0; i < 4; i++) {
        sleep(250);
        cookie = readCookie(url);
        if (cookie != null && !cookie.isEmpty()) {
            return cookie;
        }
    }
    return cookie == null ? "" : cookie;
}

private static void warmUpWebView(final Context app) {
    new Handler(Looper.getMainLooper()).post(() -> {
        try {
            new WebView(app).destroy();
        } catch (Throwable ignored) {
            // 设备没有 WebView 或已禁用时，退回兜底 Cookie
        }
    });
}
```

`readCookie` 仍用主线程 + `CountDownLatch` 等待；预热只能放主线程。

### 3.2 只有可信来源的 Cookie 才允许判定「登录失效」

```java
String cookie = cookieFor(app, url);
boolean fromManager = cookie != null && !cookie.isEmpty();
if (!fromManager) {
    cookie = meta.optString("cookie", "");   // 仅作兜底去请求
}
...
if (status < 200 || status >= 300 || !trimmed.startsWith("{")) {
    if (fromManager) {
        return failLoginExpired(app, result);      // 可信来源 → 判失效、发通知
    }
    return fail(app, result, "登录状态不可用");      // 兜底 Cookie → 只报普通错误
}
```

同理，保活任务里的 `markExpired()` 也只允许在 `fromManager == true` 时调用。

### 3.3 刷新成功后滚动保存最新 Cookie

- 每次成功刷新后把本次实际使用的 Cookie 写回持久化兜底（响应里有 `Set-Cookie` 时优先合并新值：同名覆盖、其余保留、只取 `name=value`）。
- 重写 meta 文件时，先继承旧的 `cookie` / `cookieAt` 字段，避免把兜底值覆盖掉。

```java
capture.saveCookie(url, mergeSetCookies(cookie, setCookies));
```

## 4. 验证方法（可复用到下游）

1. 强制停止应用，确保进程被杀：
   `adb shell am force-stop <包名>`
2. 重新启动应用（此时 WebView 尚未初始化），直接触发一次后台刷新。
3. 观察日志（只打印状态码、Cookie 来源、是否 JSON，**不要打印 Cookie 或 URL**）：

```
cookie fromManager=true
refresh status=200 chars=13598
```

4. 修复前的表现：冷进程第一次读 Cookie 得到空 → 回退旧 Cookie → `status=302` → 误报过期；同一进程 20 秒后再跑一次又变成 `status=200`。

## 5. 下游移植清单

- [ ] 找出所有在后台读取 `CookieManager.getCookie()` 的位置（JobService、Receiver、WorkManager Worker、后台线程）。
- [ ] 读取前预热 WebView（主线程 `new WebView(app).destroy()`），并做有限次重试。
- [ ] 记录「Cookie 是否来自 CookieManager」，作为判定登录失效的可信度依据。
- [ ] 兜底 Cookie 失败时不要清除登录态、不要发过期通知，只按普通网络/刷新失败处理。
- [ ] 成功刷新后滚动保存最新 Cookie；重写持久化文件时保留旧 Cookie 字段。
- [ ] 日志只记录状态码、来源、是否 JSON，禁止输出 Cookie、带 token 的 URL、响应体。

## 6. 注意事项

- `new WebView(context)` 可能抛异常（设备无 WebView、被禁用、进程状态异常），必须 `catch (Throwable)`。
- 预热只做一次即可，不要每次请求都创建 WebView；本实现只在首次读取为空时触发。
- 保活/刷新失败分三类：网络异常（重试）、服务端临时错误（5xx，重试）、登录失效（3xx/401/403/非 JSON 且 Cookie 可信），不要混为一谈。
- 服务端会话仍可能因为长时间 Doze、断网而真正过期；此时应发通知引导用户重新登录，登录成功后清除过期标记并恢复定时任务。
