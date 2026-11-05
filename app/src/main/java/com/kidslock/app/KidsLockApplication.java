package com.kidslock.app;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 应用全局入口，进程启动时最早执行。
 * 确保 Web 监听服务和计时服务在 Activity 创建前就已启动，
 * 避免 singleTask Activity 的 onCreate/onNewIntent 被系统延迟或拦截。
 */
public class KidsLockApplication extends Application {
    private static final String TAG = "KidsLockApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.w(TAG, "Application onCreate，启动 Web 监听服务和计时服务");

        // 直接启动 WebConfigServer，不依赖 Service 异步回调
        WebConfigServer server = WebConfigServer.getInstance(getApplicationContext());
        if (!server.isRunning()) {
            Log.w(TAG, "Web 监听服务未运行，直接启动");
            server.start();
        } else {
            Log.w(TAG, "Web 监听服务已运行");
        }

        // 启动计时服务（foreground service）
        try {
            Intent svc = new Intent(this, LockService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            Log.w(TAG, "LockService 已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动 LockService 失败", e);
        }

        // 启动 WebConfigService 前台服务保持常驻
        try {
            Intent webSvc = new Intent(this, WebConfigService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(webSvc);
            } else {
                startService(webSvc);
            }
            Log.w(TAG, "WebConfigService 已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动 WebConfigService 失败", e);
        }
    }
}
