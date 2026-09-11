package com.lyalnr.torchlight;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 无障碍服务：模拟触摸(点击/摇杆) + 截图 + 悬浮面板
 */
public class AssistService extends AccessibilityService {

    private static AssistService instance;
    private WindowManager windowManager;
    private LinearLayout floatPanel;
    private LinearLayout floatContent;   // 可折叠的内容区
    private TextView floatLog;
    private boolean floatAdded = false;
    private boolean minimized = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager.LayoutParams floatParams;

    // 拖动相关
    private float dragStartX, dragStartY;
    private int dragStartWX, dragStartWY;

    public interface ScreenshotCallback {
        void onScreenshot(Bitmap bitmap);
    }

    public static AssistService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        logLine("无障碍服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        removeFloatPanel();
    }

    /** 点击 */
    public void tap(float x, float y, final Runnable after) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        GestureDescription.Builder b = new GestureDescription.Builder();
        Path p = new Path();
        p.moveTo(x, y);
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 60));
        dispatchGesture(b.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { if (after != null) after.run(); }
            @Override public void onCancelled(GestureDescription g) { if (after != null) after.run(); }
        }, handler);
    }

    /** 摇杆拖动 */
    public void swipe(float x0, float y0, float x1, float y1, long holdMs, final Runnable after) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        GestureDescription.Builder b = new GestureDescription.Builder();
        Path p = new Path();
        p.moveTo(x0, y0);
        p.lineTo(x1, y1);
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, holdMs));
        dispatchGesture(b.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { if (after != null) after.run(); }
            @Override public void onCancelled(GestureDescription g) { if (after != null) after.run(); }
        }, handler);
    }

    /** 截图（Android 11+） */
    public void takeScreenshot(final ScreenshotCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            logLine("截图需要 Android 11+");
            if (callback != null) callback.onScreenshot(null);
            return;
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                Bitmap bmp = null;
                if (screenshot != null && screenshot.getHardwareBuffer() != null) {
                    try {
                        Bitmap tmp = Bitmap.wrapHardwareBuffer(
                                screenshot.getHardwareBuffer(), screenshot.getColorSpace());
                        if (tmp != null) {
                            bmp = tmp.copy(Bitmap.Config.ARGB_8888, false);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                if (callback != null) callback.onScreenshot(bmp);
            }

            @Override
            public void onFailure(int errorCode) {
                logLine("截图失败 code=" + errorCode);
                if (callback != null) callback.onScreenshot(null);
            }
        });
    }

    /** 保存当前截图到公共 Download 目录（用于调试取色） */
    public void saveScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            logLine("截图需要 Android 11+");
            return;
        }
        takeScreenshot(new ScreenshotCallback() {
            @Override
            public void onScreenshot(Bitmap bmp) {
                if (bmp == null) {
                    logLine("截图失败");
                    return;
                }
                try {
                    java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS);
                    java.io.File f = new java.io.File(dir,
                            "torch_" + System.currentTimeMillis() + ".png");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                    logLine("已保存: " + f.getName());
                } catch (Exception e) {
                    logLine("保存失败: " + e.getMessage());
                }
            }
        });
    }

    /** 悬浮面板 */
    public void showFloatPanel() {
        if (floatAdded) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatPanel = new LinearLayout(this);
        floatPanel.setOrientation(LinearLayout.VERTICAL);
        floatPanel.setBackgroundColor(0xCC000000);
        floatPanel.setPadding(16, 12, 16, 12);

        // 标题栏（可拖动）
        TextView title = new TextView(this);
        title.setText("起号助手（拖动我）");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(12);
        title.setPadding(0, 0, 0, 8);
        floatPanel.addView(title);

        // 内容区（可折叠）
        floatContent = new LinearLayout(this);
        floatContent.setOrientation(LinearLayout.VERTICAL);

        floatLog = new TextView(this);
        floatLog.setTextColor(0xFF00FF00);
        floatLog.setTextSize(10);
        floatLog.setText("就绪\n");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(460, 200);
        floatContent.addView(floatLog, lp);

        Button btnFollow = new Button(this);
        btnFollow.setText("开始跟引导石");
        btnFollow.setOnClickListener(v -> BotEngine.getInstance().toggleFollow());
        floatContent.addView(btnFollow);

        Button btnStop = new Button(this);
        btnStop.setText("停止");
        btnStop.setOnClickListener(v -> BotEngine.getInstance().stop());
        floatContent.addView(btnStop);

        Button btnMin = new Button(this);
        btnMin.setText("最小化/展开");
        btnMin.setOnClickListener(v -> toggleMinimize());
        floatContent.addView(btnMin);

        Button btnSave = new Button(this);
        btnSave.setText("保存截图(取色)");
        btnSave.setOnClickListener(v -> saveScreenshot());
        floatContent.addView(btnSave);

        floatPanel.addView(floatContent);

        // 标题栏拖动
        title.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    dragStartX = event.getRawX();
                    dragStartY = event.getRawY();
                    dragStartWX = floatParams.x;
                    dragStartWY = floatParams.y;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    floatParams.x = dragStartWX + (int) (event.getRawX() - dragStartX);
                    floatParams.y = dragStartWY + (int) (event.getRawY() - dragStartY);
                    try { windowManager.updateViewLayout(floatPanel, floatParams); } catch (Exception ignored) {}
                    return true;
            }
            return false;
        });

        floatParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        floatParams.gravity = Gravity.TOP | Gravity.START;
        floatParams.x = 20;
        floatParams.y = 200;
        try {
            windowManager.addView(floatPanel, floatParams);
            floatAdded = true;
        } catch (Exception e) {
            logLine("悬浮窗添加失败: " + e.getMessage());
        }
    }

    private void toggleMinimize() {
        if (floatContent == null) return;
        minimized = !minimized;
        floatContent.setVisibility(minimized ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    public void removeFloatPanel() {
        if (floatAdded && windowManager != null && floatPanel != null) {
            try { windowManager.removeView(floatPanel); } catch (Exception ignored) {}
        }
        floatAdded = false;
    }

    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int getScreenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    public void logLine(String s) {
        final String msg = s;
        handler.post(() -> {
            if (floatLog != null) {
                floatLog.setText(msg + "\n" + floatLog.getText().toString());
            }
            android.util.Log.i("TorchBot", msg);
        });
    }
}