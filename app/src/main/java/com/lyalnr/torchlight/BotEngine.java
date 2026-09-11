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

    // 橙色菱形引导石搜索区域（中右区域）
    private final float[] STONE_REGION = {0.30f, 0.30f, 0.95f, 0.80f};

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

        // 找最靠下的橙色菱形引导石（离角色最近的那个）
        ImageFinder.Result stone = ImageFinder.findLowestCluster(
                bmp, (r, g, b) -> ImageFinder.isOrange(r, g, b), STONE_REGION);

        if (stone == null) {
            svc.logLine("未找到引导石");
            handler.postDelayed(() -> loop(), 600);
            return;
        }

        // 目标在屏幕中的偏差（以角色位置为参照，角色在屏幕中央偏下）
        float dx = stone.x - 0.5f;
        float dy = stone.y - 0.55f;

        svc.logLine(String.format("引导石@(%.2f,%.2f) 偏移(%.2f,%.2f)", stone.x, stone.y, dx, dy));

        // 如果目标已经很近（屏幕中央偏下），前进并稍等
        if (Math.abs(dx) < 0.05f && Math.abs(dy) < 0.12f) {
            svc.logLine("接近引导石，前进");
            move(w, h, 0f, -0.6f, 250);
            return;
        }

        // 朝目标方向推动摇杆
        float moveX = clamp(dx * 2.5f, -1f, 1f);
        float moveY = clamp(dy * 2.0f, -0.8f, 0.8f);

        move(w, h, moveX, moveY, 300);
    }

    private void move(int w, int h, float dirX, float dirY, long holdMs) {
        final AssistService svc = AssistService.getInstance();
        if (svc == null) { stop(); return; }

        float fromX = STICK_CX * w;
        float fromY = STICK_CY * h;
        float toX = (STICK_CX + dirX * STICK_R) * w;
        float toY = (STICK_CY + dirY * STICK_R) * h;

        svc.logLine(String.format("摇杆:(%.0f,%.0f)->(%.0f,%.0f)", fromX, fromY, toX, toY));

        svc.swipe(fromX, fromY, toX, toY, holdMs, () ->
                handler.postDelayed(() -> loop(), 150));
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}