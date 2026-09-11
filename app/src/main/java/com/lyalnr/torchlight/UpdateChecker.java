package com.lyalnr.torchlight;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 检测更新：从 GitHub Releases 下载最新 APK 并提示安装
 */
public class UpdateChecker {

    // GitHub 仓库地址（最新 APK 发布页）
    private static final String RELEASE_URL =
            "https://github.com/lyalnr/torchlight-bot/releases/latest/download/torchlight-bot.apk";

    private final Handler handler = new Handler(Looper.getMainLooper());

    public void check(final Context ctx) {
        // 简单实现：直接尝试下载，成功则提示安装
        handler.post(() -> Toast.makeText(ctx, "正在检查更新...", Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            boolean hasUpdate = checkUpdateAvailable();
            if (!hasUpdate) {
                handler.post(() -> Toast.makeText(ctx, "已是最新版本", Toast.LENGTH_SHORT).show());
                return;
            }
            handler.post(() -> showUpdateDialog(ctx));
        }).start();
    }

    private boolean checkUpdateAvailable() {
        // 这里简化：直接检查 Releases 上是否有 APK 可下载
        // 后续可加版本号比对
        HttpURLConnection conn = null;
        try {
            URL url = new URL(RELEASE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void showUpdateDialog(final Context ctx) {
        new AlertDialog.Builder(ctx)
                .setTitle("发现新版本")
                .setMessage("是否下载并安装最新版？")
                .setPositiveButton("更新", (d, w) -> downloadAndInstall(ctx))
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadAndInstall(final Context ctx) {
        handler.post(() -> Toast.makeText(ctx, "正在下载更新...", Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            try {
                // 下载到 App 私有缓存目录（无需存储权限）
                File apk = new File(ctx.getExternalCacheDir(), "update.apk");
                HttpURLConnection conn = (HttpURLConnection) new URL(RELEASE_URL).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(apk);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.close();
                in.close();
                conn.disconnect();

                // 调起系统安装（用 FileProvider）
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        ctx, ctx.getPackageName() + ".fileprovider", apk);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(ctx, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}