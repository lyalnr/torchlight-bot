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
    private TextView floatLog;
    private boolean floatAdded = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

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

    /** 悬浮面板 */
    public void showFloatPanel() {
        if (floatAdded) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatPanel = new LinearLayout(this);
        floatPanel.setOrientation(LinearLayout.VERTICAL);
        floatPanel.setBackgroundColor(0xCC000000);
        floatPanel.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("起号调试");
        title.setTextColor(0xFFFFFFFF);
        floatPanel.addView(title);

        floatLog = new TextView(this);
        floatLog.setTextColor(0xFF00FF00);
        floatLog.setTextSize(10);
        floatLog.setText("就绪\n");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(500, 260);
        floatPanel.addView(floatLog, lp);

        Button btnFollow = new Button(this);
        btnFollow.setText("开始跟箭头");
        btnFollow.setOnClickListener(v -> BotEngine.getInstance().toggleFollow());
        floatPanel.addView(btnFollow);

        Button btnTestTap = new Button(this);
        btnTestTap.setText("测试点击屏幕中心");
        btnTestTap.setOnClickListener(v -> tap(getScreenWidth() / 2f, getScreenHeight() / 2f, null));
        floatPanel.addView(btnTestTap);

        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.START;
        wlp.x = 20;
        wlp.y = 20;
        try {
            windowManager.addView(floatPanel, wlp);
            floatAdded = true;
        } catch (Exception e) {
            logLine("悬浮窗添加失败: " + e.getMessage());
        }
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