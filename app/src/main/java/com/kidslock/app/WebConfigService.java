package com.kidslock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class WebConfigService extends Service {
    private static final String TAG = "WebConfigService";
    private static final String CHANNEL_ID = "web_config_channel";
    private static final int NOTIFICATION_ID = 2001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.i(TAG, "WebConfigService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        WebConfigServer.getInstance(this).start();
        Log.i(TAG, "Web config server started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        WebConfigServer.getInstance(this).stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "网页配置服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("本地网页配置界面");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        String url = WebConfigServer.getInstance(this).getServerUrl();
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("儿童锁屏网页配置已启动")
                .setContentText(url)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, WebConfigService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * 确保 Web 监听服务运行；若未启动则启动，并记录日志。
     */
    public static void ensureRunning(Context context) {
        WebConfigServer server = WebConfigServer.getInstance(context.getApplicationContext());
        if (server == null || !server.isRunning()) {
            Log.i("WebConfigService", "Web 监听服务未运行，正在启动");
            start(context);
        } else {
            Log.i("WebConfigService", "Web 监听服务已运行，无需重复启动");
        }
    }
}
