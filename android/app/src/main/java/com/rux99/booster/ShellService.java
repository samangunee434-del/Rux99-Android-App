package com.rux99.booster;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs with Shizuku's shell identity. Only named, fixed operations are accepted. */
public class ShellService extends IShellService.Stub {
    public ShellService() {}
    @Override public void destroy() { System.exit(0); }
    private static String[] commands(String action) {
        switch (action) {
            case "anim": return new String[]{
                "settings put global window_animation_scale 0",
                "settings put global transition_animation_scale 0",
                "settings put global animator_duration_scale 0"};
            case "gpu": return new String[]{"settings put global force_gpu_rendering 1"};
            case "compile": return new String[]{"cmd package compile -m speed-profile -f com.dts.freefireth"};
            case "launch": return new String[]{"monkey -p com.dts.freefireth -c android.intent.category.LAUNCHER 1"};
            case "performance": return new String[]{
                "cmd power set-fixed-performance-mode-enabled true",
                "cmd game mode performance com.dts.freefireth",
                "dumpsys deviceidle whitelist +com.dts.freefireth",
                "am kill-all"};
            case "restore": return new String[]{
                "settings put global window_animation_scale 1",
                "settings put global transition_animation_scale 1",
                "settings put global animator_duration_scale 1",
                "settings put global force_gpu_rendering 0",
                "cmd power set-fixed-performance-mode-enabled false",
                "cmd game mode standard com.dts.freefireth",
                "dumpsys deviceidle whitelist -com.dts.freefireth"};
            default: throw new IllegalArgumentException("Unknown operation");
        }
    }
    @Override public synchronized String execute(String action) {
        StringBuilder output = new StringBuilder();
        boolean ok = true;
        try {
            List<String> list = new ArrayList<>();
            if ("boost".equals(action)) {
                for (String group : new String[]{"anim", "gpu", "performance", "compile", "launch"})
                    java.util.Collections.addAll(list, commands(group));
            } else java.util.Collections.addAll(list, commands(action));
            // Check the installed target first. No optimization is applied to a missing game.
            if (!"restore".equals(action)) {
                JSONObject installed = run("pm path com.dts.freefireth");
                if (!installed.getBoolean("ok") || !installed.getString("output").contains("package:"))
                    throw new IllegalStateException("Free Fire (com.dts.freefireth) is not installed. Free Fire MAX uses a different package and is not supported by this version.");
            }
            for (String command : list) {
                JSONObject result = run(command);
                boolean passed = result.getBoolean("ok");
                ok &= passed;
                output.append("$ ").append(command).append('\n')
                    .append(passed ? "[OK] " : "[FAILED] ").append(result.getString("output")).append('\n');
            }
        } catch (Exception error) {
            ok = false;
            output.append(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
        try { return new JSONObject().put("action", action).put("ok", ok).put("output", output.toString()).toString(); }
        catch (Exception error) { return "{\"ok\":false,\"action\":\"error\",\"output\":\"Could not encode command result\"}"; }
    }
    private JSONObject run(String command) throws Exception {
        Process process = new ProcessBuilder("/system/bin/sh", "-c", command).redirectErrorStream(true).start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (java.io.InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[4096]; int size;
                while ((size = stream.read(buffer)) != -1) {
                    if (captured.size() < 65536) captured.write(buffer, 0, Math.min(size, 65536 - captured.size()));
                }
            } catch (Exception ignored) {}
        });
        reader.start();
        boolean finished = process.waitFor(90, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        reader.join(2000);
        String text = captured.toString(StandardCharsets.UTF_8.name()).trim();
        boolean ok = finished && process.exitValue() == 0;
        // Some Android shell tools report unsupported operations in text with exit code 0.
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("error") || lower.contains("exception") || lower.contains("not supported") || lower.contains("unsupported") || lower.contains("not found") || lower.contains("failure") || lower.contains("unknown command") || lower.contains("permission denial") || lower.contains("aborted")) ok = false;
        return new JSONObject().put("ok", ok).put("output", finished ? (text.isEmpty() ? "Command returned exit code " + process.exitValue() + ". Effect depends on device support." : text) : "Timed out after 90 seconds.");
    }
}
