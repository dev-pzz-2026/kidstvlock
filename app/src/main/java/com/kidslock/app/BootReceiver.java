package com.kidslock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 开机自启动接收器。
 * - 如果当前处于锁屏状态 → 直接弹锁屏界面
 * - 如果计时器正在运行 → 恢复计时（检查是否已超时）
 * - 如果启用了开机自启 → 启动计时服务
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    static final String ACTION_WAKEUP = "com.kidslock.app.ACTION_WAKEUP";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Boot received: " + action);
        if (action == null) return;

        // 闹钟唤醒：检查计时器是否已超时，超时则锁屏
        if (ACTION_WAKEUP.equals(action)) {
            Log.i(TAG, "Wakeup alarm received");
            PrefManager pref = new PrefManager(context);
            if (pref.isTimerExpired()) {
                Log.i(TAG, "Timer expired on wakeup, locking");
                // 先启动 LockService，由 onStartCommand 统一处理锁屏，避免重复弹 Activity
                startLockService(context);
                startWebConfigService(context);
                LockService.lockAndShow(context);
            } else {
                Log.i(TAG, "Timer not expired on wakeup, resuming service");
                startLockService(context);
            }
            return;
        }

        boolean isBoot = action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals("android.intent.action.QUICKBOOT_POWERON")
                || action.equals("com.android.intent.action.BOOT_COMPLETED");

        if (!isBoot) return;

        PrefManager pref = new PrefManager(context);

        // 如果已经处于锁定状态，启动服务，由 LockService watchdog 持续拉起锁屏界面
        if (pref.isLocked()) {
            Log.i(TAG, "Device was locked, starting services, LockService watchdog will show lock screen");
            pref.setHomeAliasEnabled(context, true);
            startLockService(context);
            startWebConfigService(context);
            return;
        }

        // 如果计时器正在运行
        if (pref.isTimerActive()) {
            if (pref.isTimerExpired()) {
                // 计时已超时 → 锁屏
                Log.i(TAG, "Timer expired during boot, locking");
                pref.setLocked(true);
                pref.setHomeAliasEnabled(context, true);
                pref.stopTimer();
                startLockService(context);
                startWebConfigService(context);
                return;
            } else {
                // 计时未超时 → 恢复计时服务
                Log.i(TAG, "Timer still running, resuming service");
                startLockService(context);
                startWebConfigService(context);
            }
            return;
        }

        // 开机自启 → 启动计时
        if (pref.isAutoStart()) {
            Log.i(TAG, "Auto start enabled, starting timer");
            int minutes = pref.getWatchLimitMinutes();
            pref.startTimer(minutes);
            startLockService(context);
            startWebConfigService(context);
        }
    }

    private void startLockService(Context context) {
        Intent serviceIntent = new Intent(context, LockService.class);
        serviceIntent.putExtra("action", "start_timer");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    private void startWebConfigService(Context context) {
        Intent serviceIntent = new Intent(context, WebConfigService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
