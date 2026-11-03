package com.rux99.booster;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private WebView web;
    private volatile IShellService shell;
    private boolean binding = false;
    private boolean destroyed = false;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Shizuku.UserServiceArgs serviceArgs = new Shizuku.UserServiceArgs(
        new ComponentName("com.rux99.booster", ShellService.class.getName()))
        .daemon(false).processNameSuffix("shell").debuggable(false).version(1);
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            binding = false;
            shell = IShellService.Stub.asInterface(binder);
            state("Connected to Shizuku. Commands run with Android shell permission.");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            shell = null; binding = false;
            state("Shizuku disconnected. Start Shizuku and reconnect.");
        }
    };
    private final Shizuku.OnBinderReceivedListener received = () -> runOnUiThread(this::bindIfAllowed);
    private final Shizuku.OnBinderDeadListener dead = () -> runOnUiThread(() -> {
        shell = null; binding = false; state("Shizuku stopped. Start Shizuku and reconnect.");
    });
    private final Shizuku.OnRequestPermissionResultListener permission = (request, grant) -> runOnUiThread(() -> {
        if (request != 99) return;
        if (grant == PackageManager.PERMISSION_GRANTED) bindIfAllowed();
        else state("Permission denied. Allow Rux99 in Shizuku, then reconnect.");
    });
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff0b0e14);
        getWindow().setNavigationBarColor(0xff0b0e14);
        web = new WebView(this);
        setContentView(web);
        web.setBackgroundColor(0xff0b0e14);
        web.setPadding(0, 0, 0, 0);
        web.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse local = loader.shouldInterceptRequest(request.getUrl());
                return local != null ? local : new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())
                    && "/assets/index.html".equals(uri.getPath())) return false;
                if ("https".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) { bindIfAllowed(); }
        });
        web.addJavascriptInterface(new Bridge(), "Rux99Native");
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addRequestPermissionResultListener(permission);
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }
    private void bindIfAllowed() {
        try {
            if (!Shizuku.pingBinder()) { state("Start Shizuku first, then tap Connect Shizuku."); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                state("Tap Connect Shizuku and allow Rux99 in the permission prompt."); return;
            }
            if (shell != null && shell.asBinder().isBinderAlive()) { state("Connected to Shizuku."); return; }
            if (!binding) {
                binding = true; state("Connecting to Shizuku shell service…");
                Shizuku.bindUserService(serviceArgs, connection);
                web.postDelayed(() -> {
                    if (!destroyed && binding && shell == null) {
                        binding = false;
                        state("Connection timed out. Restart Shizuku and reconnect.");
                    }
                }, 15000);
            }
        } catch (Exception error) { binding = false; state("Shizuku connection failed: " + error.getMessage()); }
    }
    private void emit(String function, String json) {
        runOnUiThread(() -> { if (!destroyed) web.evaluateJavascript("window." + function + "(" + json + ")", null); });
    }
    private void state(String message) {
        try {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
            emit("onRux99State", new JSONObject().put("connected", shell != null && shell.asBinder().isBinderAlive())
                .put("message", message).put("ram", String.format(java.util.Locale.US, "%.1f GB", memory.availMem / 1073741824.0)).toString());
        } catch (Exception ignored) {}
    }
    private void failure(String action, String message) {
        try { emit("onRux99Result", new JSONObject().put("action", action).put("ok", false).put("output", message).toString()); }
        catch (Exception ignored) {}
    }
    private class Bridge {
        @JavascriptInterface public void connect() {
            runOnUiThread(() -> {
                try {
                    if (!Shizuku.pingBinder()) { state("Shizuku is not running. Start it using wireless debugging or ADB."); return; }
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(99);
                    else bindIfAllowed();
                } catch (Exception error) { state("Cannot connect: " + error.getMessage()); }
            });
        }
        @JavascriptInterface public void status() { runOnUiThread(() -> bindIfAllowed()); }
        @JavascriptInterface public void run(String action) {
            if (!java.util.Arrays.asList("boost", "anim", "gpu", "compile", "launch", "restore").contains(action)) {
                failure(action, "Unknown operation."); return;
            }
            if (!running.compareAndSet(false, true)) { failure(action, "Another operation is running."); return; }
            worker.execute(() -> {
                try {
                    IShellService current = shell;
                    if (current == null || !Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        failure(action, "Connect Shizuku and grant permission first. No commands were run."); return;
                    }
                    emit("onRux99Result", current.execute(action));
                    state("Connected. Review command output for device support.");
                } catch (Exception error) { failure(action, "Command failed: " + error.getMessage()); }
                finally { running.set(false); }
            });
        }
    }
    @Override protected void onDestroy() {
        destroyed = true;
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        try { Shizuku.unbindUserService(serviceArgs, connection, true); } catch (Exception ignored) {}
        worker.shutdownNow();
        web.removeJavascriptInterface("Rux99Native");
        web.destroy();
        super.onDestroy();
    }
}
