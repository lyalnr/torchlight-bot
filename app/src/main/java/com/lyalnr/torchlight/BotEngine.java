package com.lyalnr.torchlight;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

/**
 * 主引擎状态机：
 * 识别主线任务 → 点击 → 出现引导石 → 跟引导石走 → 到NPC → 循环
 */
public class BotEngine {

    private static BotEngine sInstance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 浮动摇杆按下落点（归一化，左下角）
    private final float STICK_CX = 0.10f;
    private final float STICK_CY = 0.80f;
    private final float STICK_R = 0.14f;

    // "主线"任务按钮位置（左侧顶部，固定常驻）归一化坐标
    private final float MAIN_X = 0.08f;
    private final float MAIN_Y = 0.10f;

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
        if (svc != null) svc.logLine("开始起号循环");
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
                decide(bmp);
            }
        });
    }

    /**
     * 每一步决策：
     * 1. 找引导石。找到了 → 朝它走
     * 2. 没找到 → 点一下"主线"，等引导石出现
     */
    private void decide(Bitmap bmp) {
        final AssistService svc = AssistService.getInstance();
        if (svc == null) { stop(); return; }

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        ImageFinder.Result stone = ImageFinder.findLowestCluster(
                bmp, (r, g, b) -> ImageFinder.isOrange(r, g, b), STONE_REGION);

        if (stone != null) {
            float dx = stone.x - 0.5f;
            float dy = stone.y - 0.55f;
            svc.logLine(String.format("引导石@(%.2f,%.2f) 偏移(%.2f,%.2f)", stone.x, stone.y, dx, dy));

            if (Math.abs(dx) < 0.06f && stone.y > 0.40f) {
                float dirX = clamp(dx * 3f, -1f, 1f);
                move(w, h, dirX, -0.7f, 280);
                return;
            }

            float moveX = clamp(dx * 2.5f, -1f, 1f);
            float moveY = clamp(dy * 2.0f, -0.8f, 0.8f);
            move(w, h, moveX, moveY, 300);
        } else {
            svc.logLine("无引导石，点击主线任务");
            svc.tap(MAIN_X * w, MAIN_Y * h, () ->
                    handler.postDelayed(() -> loop(), 1500));
        }
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