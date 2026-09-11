package com.lyalnr.torchlight;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView tvStatus;

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
        btnCheckUpdate.setOnClickListener(v -> new UpdateChecker().check(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvStatus.setText(AssistService.isRunning() ? "状态：无障碍已连接" : "状态：无障碍未连接");
    }
}