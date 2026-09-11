package com.lyalnr.torchlight;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

/**
 * 主引擎：截图 → 找金色箭头 → 摇杆朝箭头移动 → 循环
 */
public class BotEngine {

    private static BotEngine sInstance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 浮动摇杆按下落点（归一化，左下角）
    private final float STICK_CX = 0.10f;
    private final float STICK_CY = 0.80f;
    private final float STICK_R = 0.12f;

    // 金色箭头搜索区域（屏幕中央附近）
    private final float[] GOLD_REGION = {0.30f, 0.30f, 0.70f, 0.75f};

    private boolean running = false;

    public static BotEngine getInstance() {
        if (sInstance == null) sInstance = new BotEngine();
        return sInstance;
    }

    public void toggleFollow() {
        if (running) stop(); else start();
    }

    public void start() {
        if (running) return;
        running = true;
        AssistService svc = AssistService.getInstance();
        if (svc != null) svc.logLine("开始跟箭头循环");
        loop();
    }

    public void stop() {
        running = false;
        AssistService svc = AssistService.getInstance();
        if (svc != null) svc.logLine("停止");
    }

    private void loop() {
        if (!running) return;
        final AssistService svc = AssistService.getInstance();
        if (svc == null) {
            stop();
            return;
        }
        svc.takeScreenshot(new AssistService.ScreenshotCallback() {
            @Override
            public void onScreenshot(Bitmap bmp) {
                if (!running) return;
                if (bmp == null) {
                    AssistService.getInstance().logLine("截图失败");
                    handler.postDelayed(() -> loop(), 500);
                    return;
                }
                step(bmp);
            }
        });
    }

    private void step(Bitmap bmp) {
        final AssistService svc = AssistService.getInstance();
        if (svc == null) { stop(); return; }

        int w = bmp.getWidth();
        int h = bmp.getHeight();
        svc.logLine("截图 " + w + "x" + h);

        ImageFinder.Result arrow = ImageFinder.findColorCentroid(
                bmp, (r, g, b) -> ImageFinder.isGold(r, g, b), GOLD_REGION);

        if (arrow == null) {
            svc.logLine("未找到金色箭头");
            handler.postDelayed(() -> loop(), 800);
            return;
        }

        float dx = arrow.x - 0.5f;
        float dy = arrow.y - 0.5f;

        svc.logLine(String.format("箭头@(%.2f,%.2f) 偏移(%.2f,%.2f)", arrow.x, arrow.y, dx, dy));

        if (Math.abs(dx) < 0.03f && Math.abs(dy) < 0.03f) {
            svc.logLine("已到达箭头位置");
            handler.postDelayed(() -> loop(), 800);
            return;
        }

        float moveX = clamp(dx * 3.0f, -1f, 1f);
        float moveY = clamp(dy * 3.0f, -1f, 1f);

        float fromX = STICK_CX * w;
        float fromY = STICK_CY * h;
        float toX = (STICK_CX + moveX * STICK_R) * w;
        float toY = (STICK_CY + moveY * STICK_R) * h;

        svc.logLine(String.format("摇杆:(%.0f,%.0f)->(%.0f,%.0f)", fromX, fromY, toX, toY));

        svc.swipe(fromX, fromY, toX, toY, 300, () ->
                handler.postDelayed(() -> loop(), 200));
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}