package com.lyalnr.torchlight;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private static final int REQ_PERM = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);

        Button btnStart = findViewById(R.id.btnStart);
        Button btnEnableAccess = findViewById(R.id.btnEnableAccess);
        Button btnEnableOverlay = findViewById(R.id.btnEnableOverlay);

        btnStart.setOnClickListener(v -> {
            if (!AssistService.isRunning()) {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                return;
            }
            AssistService.getInstance().showFloatPanel();
            tvStatus.setText("状态：已启动");
            Toast.makeText(this, "悬浮窗已显示", Toast.LENGTH_SHORT).show();
        });

        btnEnableAccess.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        btnEnableOverlay.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        });

        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnCheckUpdate.setOnClickListener(v -> {
            checkAndRequestPerms(() -> new UpdateChecker().check(this));
        });

        // 启动时主动申请权限
        checkAndRequestPerms(null);
    }

    /**
     * 检查并申请运行时权限（存储读写 + 安装未知应用）
     */
    private void checkAndRequestPerms(Runnable after) {
        // 存储权限（Android 9 及以下需要）
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQ_PERM);
                return;
            }
        }
        // Android 8+ 需要"允许安装未知应用"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "请允许安装未知应用", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (after != null) after.run();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            boolean ok = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            }
            Toast.makeText(this, ok ? "权限已授予" : "部分权限未授予", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvStatus.setText(AssistService.isRunning() ? "状态：无障碍已连接" : "状态：无障碍未连接");
    }
}