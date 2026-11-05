package com.kidslock.app;

import android.app.AlarmManager;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class LockService extends Service {

    private static final String TAG = "LockService";
    private static final String CHANNEL_ID = "kids_lock_timer";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_WAKEUP = "com.kidslock.app.ACTION_WAKEUP";

    private PrefManager pref;
    private Handler handler;
    private Runnable tickRunnable;
    private Runnable watchdogRunnable;

    private WindowManager windowManager;
    private View overlayView;

    @Override
    public void onCreate() {
        super.onCreate();
        pref = new PrefManager(this);
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createNotificationChannel();
        Log.i(TAG, "LockService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());

        // 进程死后重启：检查是否已过期，直接锁屏
        if (pref.isTimerExpired()) {
            Log.i(TAG, "Service restarted, timer already expired, locking now");
            triggerLock();
            return START_STICKY;
        }

        // ACTION_UNLOCK → 解锁并重新启动计时
        if (intent != null && "com.kidslock.app.ACTION_UNLOCK".equals(intent.getAction())) {
            Log.i(TAG, "ACTION_UNLOCK received, unlocking and restarting timer");
            pref.setLocked(false);
            pref.setHomeAliasEnabled(this, false);
            hideOverlay();
            // 取消守护回调
            if (watchdogRunnable != null) {
                handler.removeCallbacks(watchdogRunnable);
                watchdogRunnable = null;
            }
            // 取消已有计时回调
            if (tickRunnable != null) {
                handler.removeCallbacks(tickRunnable);
                tickRunnable = null;
            }
            // 如果计时器已激活，开始计时循环
            if (pref.isTimerActive()) {
                startTicking();
            }
            return START_STICKY;
        }

        // 已锁屏 → 进入锁屏守护模式
        if (pref.isLocked()) {
            Log.i(TAG, "Device locked, starting lock watchdog");
            hideOverlay();
            startWatchdog();
            startLockScreenActivity();
            return START_STICKY;
        }

        // 解锁后：取消守护回调
        if (watchdogRunnable != null) {
            handler.removeCallbacks(watchdogRunnable);
            watchdogRunnable = null;
        }
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }

        // 计时器未激活 → 停止服务
        if (!pref.isTimerActive()) {
            Log.i(TAG, "Timer not active, stopping service");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // 如果已经超时，直接锁屏
        if (pref.isTimerExpired()) {
            Log.i(TAG, "Timer expired, locking now");
            triggerLock();
            return START_STICKY;
        }

        // 开始计时循环
        startTicking();
        return START_STICKY;
    }

    private void startTicking() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
        }
        long endTime = pref.getTimerEndTime();
        if (endTime > System.currentTimeMillis()) {
            scheduleWakeupAlarm(endTime);
        }
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (pref.isLocked()) {
                    hideOverlay();
                    startWatchdog();
                    startLockScreenActivity();
                    return;
                }
                if (pref.isTimerExpired()) {
                    triggerLock();
                    return;
                }
                updateNotification();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(tickRunnable);
    }

    private void triggerLock() {
        Log.i(TAG, "Triggering lock screen!");
        pref.setLocked(true);
        pref.setHomeAliasEnabled(this, true);
        pref.stopTimer();

        startLockScreenActivity();
        hideOverlay();

        startWatchdog();
    }

    /**
     * 设置闹钟后备唤醒，防止 Handler 在进程休眠/系统杀服务后不再检查超时。
     */
    private void scheduleWakeupAlarm(long triggerAtMillis) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, BootReceiver.class);
            intent.setAction(ACTION_WAKEUP);
            PendingIntent pi = PendingIntent.getBroadcast(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
            Log.i(TAG, "Wakeup alarm scheduled at " + triggerAtMillis + " (remaining " + (triggerAtMillis - System.currentTimeMillis()) + " ms)");
        } catch (Exception e) {
            Log.e(TAG, "scheduleWakeupAlarm failed", e);
        }
    }

    /**
     * 根据当前 prefs 中的计时器结束时间，重新设置闹钟后备唤醒。
     * 供 Web 配置修改 watchLimitMinutes 后调用，确保闹钟时间与新结束时间一致。
     */
    public static void rescheduleWakeupAlarm(Context context) {
        try {
            PrefManager pref = new PrefManager(context.getApplicationContext());
            long endTime = pref.getTimerEndTime();
            if (endTime > System.currentTimeMillis()) {
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(context, BootReceiver.class);
                intent.setAction(ACTION_WAKEUP);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, endTime, pi);
                }
                Log.i("LockService", "Wakeup alarm rescheduled at " + endTime + " (remaining " + (endTime - System.currentTimeMillis()) + " ms)");
            }
        } catch (Exception e) {
            Log.e("LockService", "rescheduleWakeupAlarm failed", e);
        }
    }

    private void startWatchdog() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
        if (watchdogRunnable != null) {
            handler.removeCallbacks(watchdogRunnable);
        }
        watchdogRunnable = new Runnable() {
            @Override
            public void run() {
                if (!pref.isLocked()) {
                    Log.i(TAG, "Unlocked, watchdog stopping");
                    hideOverlay();
                    stopForeground(true);
                    stopSelf();
                    return;
                }

                if (!isLockScreenOnTop()) {
                    Log.i(TAG, "Lock screen not on top, pulling again");
                    startLockScreenActivity();
                    hideOverlay();
                } else {
                    hideOverlay();
                }

                handler.postDelayed(this, 500);
            }
        };
        handler.post(watchdogRunnable);
    }

    private boolean isLockScreenOnTop() {
        // 无 GET_TASKS 权限时无法可靠获取栈顶，锁定状态下默认视为已置顶，
        // 由 LockScreenActivity 自身的 onPause 保护避免重复启动
        return pref.isLocked();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    public void hideOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove overlay", e);
            }
            overlayView = null;
        }
    }

    private void startLockScreenActivity() {
        Intent lockIntent = new Intent(this, LockScreenActivity.class);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        lockIntent.setPackage(getPackageName());
        try {
            // 使用 PendingIntent.send 绕过 Android 10+ 后台启动 Activity 限制
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, lockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pendingIntent.send();
        } catch (Exception e) {
            Log.e(TAG, "PendingIntent.start LockScreenActivity failed", e);
            try {
                startActivity(lockIntent);
            } catch (Exception ex) {
                Log.e(TAG, "Retry start LockScreenActivity failed", ex);
            }
        }
    }

    public static void requestUnlock(Context context) {
        Intent intent = new Intent(context, LockService.class);
        intent.setAction("com.kidslock.app.ACTION_UNLOCK");
        context.startService(intent);
    }

    @Override
    public void onDestroy() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
        }
        if (watchdogRunnable != null) {
            handler.removeCallbacks(watchdogRunnable);
        }
        hideOverlay();
        Log.i(TAG, "LockService destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "儿童锁屏计时",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("观看时间计时通知");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        String contentText;
        if (pref.isLocked()) {
            contentText = "电视已锁定，答对汉字即可解锁";
        } else {
            long remaining = pref.getRemainingMillis();
            contentText = "剩余观看时间：" + formatTime(remaining);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("儿童锁屏运行中")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
