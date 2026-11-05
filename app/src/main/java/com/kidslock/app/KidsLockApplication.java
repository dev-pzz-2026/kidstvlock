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

        // 启动 WebConfigService 前台服务保持常驻，并在 onStartCommand 中负责启动 Web 监听服务器
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
