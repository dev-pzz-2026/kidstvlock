package com.kidslock.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private PrefManager pref;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvRemaining;
    private Button btnAutoStart;
    private TextView tvWatchTime;
    private TextView tvUnlockCount;
    private TextView tvLockInterval;
    private TextView tvRelockDelay;
    private TextView tvLockDuration;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = new PrefManager(this);

        // 先启动 Web 监听服务，再申请权限，避免权限页面挂起后服务未启动
        ensureServicesRunning();
        android.util.Log.w("MainActivity", "App 启动，检查并启动 Web 监听服务");

        // 清理残留状态：若当前未锁定，确保 HOME alias 已禁用，避免卸载/重启后卡死
        if (!pref.isLocked()) {
            pref.setHomeAliasEnabled(this, false);
        }

        requestAllPermissions();

        if (pref.isLocked()) {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
            finish();
            return;
        }

        // 首次打开或每次打开时自动启动计时器
        if (!pref.isTimerActive()) {
            int minutes = pref.getWatchLimitMinutes();
            pref.startTimer(minutes);
            startLockService();
            WebConfigService.ensureRunning(this);
            if (!pref.isFirstLaunchShown()) {
                toastSafe("儿童锁屏已启动，默认" + minutes + "分钟后锁屏");
                pref.setFirstLaunchShown(true);
            } else {
                toastSafe("计时已启动：" + minutes + " 分钟后锁屏");
            }
            // 延长 finish 时间，确保 Toast 足够显示（部分电视需较长时间）
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 5000);
        } else {
            startLockService();
            WebConfigService.ensureRunning(this);
            long remaining = pref.getRemainingMillis();
            long min = remaining / 60000;
            long sec = (remaining / 1000) % 60;
            if (remaining <= 0) {
                toastSafe("计时已到期，即将锁屏");
            } else {
                toastSafe(String.format("计时器运行中，剩余 %02d:%02d", min, sec));
            }
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 5000);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ensureServicesRunning();
        android.util.Log.w("MainActivity", "App 前台恢复，检查并启动 Web 监听服务");
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvRemaining = findViewById(R.id.tvRemaining);
        btnAutoStart = findViewById(R.id.btnAutoStart);
        tvWatchTime = findViewById(R.id.tvWatchTime);
        tvUnlockCount = findViewById(R.id.tvUnlockCount);
        tvLockInterval = findViewById(R.id.tvLockInterval);
        tvRelockDelay = findViewById(R.id.tvRelockDelay);
        tvLockDuration = findViewById(R.id.tvLockDuration);
    }

    private void setupListeners() {
        btnAutoStart.setOnClickListener(v -> {
            boolean newVal = !pref.isAutoStart();
            pref.setAutoStart(newVal);
            updateStatus();
            toastSafe(newVal ? "已开启开机自启" : "已关闭开机自启");
        });

        int[] timeButtons = {R.id.btnTime15, R.id.btnTime30, R.id.btnTime45,
                R.id.btnTime60, R.id.btnTime90, R.id.btnTime120};
        int[] timeValues = {15, 30, 45, 60, 90, 120};
        for (int i = 0; i < timeButtons.length; i++) {
            final int minutes = timeValues[i];
            findViewById(timeButtons[i]).setOnClickListener(v -> {
                pref.setWatchLimitMinutes(minutes);
                updateStatus();
                toastSafe("观看时长设为 " + minutes + " 分钟");
            });
        }
        findViewById(R.id.btnTimeCustom).setOnClickListener(v -> showCustomWatchTimeDialog());

        int[] countButtons = {R.id.btnUnlock3, R.id.btnUnlock5, R.id.btnUnlock10};
        int[] countValues = {3, 5, 10};
        for (int i = 0; i < countButtons.length; i++) {
            final int count = countValues[i];
            findViewById(countButtons[i]).setOnClickListener(v -> {
                pref.setUnlockCount(count);
                updateStatus();
                toastSafe("解锁题数设为 " + count + " 题");
            });
        }
        findViewById(R.id.btnUnlockCustom).setOnClickListener(v -> showCustomUnlockCountDialog());

        int[] lockIntervalButtons = {R.id.btnLockInterval15, R.id.btnLockInterval30, R.id.btnLockInterval60};
        int[] lockIntervalValues = {15, 30, 60};
        for (int i = 0; i < lockIntervalButtons.length; i++) {
            final int minutes = lockIntervalValues[i];
            findViewById(lockIntervalButtons[i]).setOnClickListener(v -> {
                pref.setLockIntervalMinutes(minutes);
                updateStatus();
                toastSafe("锁屏间隔设为 " + minutes + " 分钟");
            });
        }
        findViewById(R.id.btnLockIntervalCustom).setOnClickListener(v -> showCustomLockIntervalDialog());

        int[] lockDurationButtons = {R.id.btnLockDuration0, R.id.btnLockDuration15, R.id.btnLockDuration30, R.id.btnLockDuration60};
        int[] lockDurationValues = {0, 15, 30, 60};
        for (int i = 0; i < lockDurationButtons.length; i++) {
            final int minutes = lockDurationValues[i];
            findViewById(lockDurationButtons[i]).setOnClickListener(v -> {
                pref.setLockDurationMinutes(minutes);
                updateStatus();
                toastSafe(minutes == 0 ? "锁定时长设为：手动解锁（无限）" : "锁定时长设为 " + minutes + " 分钟（超时自动解锁）");
            });
        }
        findViewById(R.id.btnLockDurationCustom).setOnClickListener(v -> showCustomLockDurationDialog());

        int[] relockButtons = {R.id.btnRelockDelay1, R.id.btnRelockDelay2, R.id.btnRelockDelay5};
        int[] relockValues = {1, 2, 5};
        for (int i = 0; i < relockButtons.length; i++) {
            final int seconds = relockValues[i];
            findViewById(relockButtons[i]).setOnClickListener(v -> {
                pref.setRelockDelaySeconds(seconds);
                updateStatus();
                toastSafe("重新锁屏延迟设为 " + seconds + " 秒");
            });
        }
        findViewById(R.id.btnRelockDelayCustom).setOnClickListener(v -> showCustomRelockDelayDialog());

        findViewById(R.id.btnStartTimer).setOnClickListener(v -> {
            int minutes = pref.getWatchLimitMinutes();
            pref.startTimer(minutes);
            startLockService();
            updateStatus();
            toastSafe("计时开始：" + minutes + " 分钟后锁屏");
        });

        findViewById(R.id.btnStopTimer).setOnClickListener(v -> {
            pref.stopTimer();
            stopLockService();
            updateStatus();
            toastSafe("计时已停止");
        });

        findViewById(R.id.btnLockNow).setOnClickListener(v -> {
            Intent intent = new Intent(this, LockScreenActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });

        findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            pref.setLocked(false);
            pref.setHomeAliasEnabled(this, false);
            pref.stopTimer();
            stopLockService();
            updateStatus();
            toastSafe("已解锁");
        });

    }

    private void updateStatus() {
        if (pref.isLocked()) {
            tvStatus.setText("状态：已锁定");
        } else if (pref.isTimerActive()) {
            tvStatus.setText("状态：计时中");
        } else {
            tvStatus.setText("状态：空闲");
        }

        if (pref.isTimerActive()) {
            long remaining = pref.getRemainingMillis();
            long min = remaining / 60000;
            long sec = (remaining / 1000) % 60;
            tvRemaining.setText(String.format("剩余：%02d:%02d", min, sec));
        } else {
            tvRemaining.setText("剩余：--:--");
        }

        btnAutoStart.setText(pref.isAutoStart() ? "开机自启：已开启" : "开机自启：已关闭");
        tvWatchTime.setText("观看时长：每次计时 " + pref.getWatchLimitMinutes() + " 分钟到时锁屏");
        tvUnlockCount.setText("解锁题数：连续答对 " + pref.getUnlockCount() + " 题才能解锁");
        if (tvLockInterval != null) {
            tvLockInterval.setText("锁屏间隔：每 " + pref.getLockIntervalMinutes() + " 分钟自动锁屏（0表示不自动）");
        }
        if (tvRelockDelay != null) {
            tvRelockDelay.setText("重新锁屏延迟：" + pref.getRelockDelaySeconds() + " 秒（离开锁屏界面后）");
        }
        if (tvLockDuration != null) {
            int d = pref.getLockDurationMinutes();
            if (d == 0) {
                tvLockDuration.setText("锁定时长：无限（需答题解锁）");
            } else {
                tvLockDuration.setText("锁定时长：锁定后 " + d + " 分钟自动解锁（0表示无限）");
            }
        }
    }

    private void showCustomWatchTimeDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("1-480");
        new AlertDialog.Builder(this)
                .setTitle("自定义观看时长（分钟）")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        if (val < 1) val = 1;
                        if (val > 480) val = 480;
                        pref.setWatchLimitMinutes(val);
                        updateStatus();
                        toastSafe("观看时长设为 " + val + " 分钟");
                    } catch (Exception e) {
                        toastSafe("请输入有效数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomUnlockCountDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("1-20");
        new AlertDialog.Builder(this)
                .setTitle("自定义解锁题数")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        if (val < 1) val = 1;
                        if (val > 20) val = 20;
                        pref.setUnlockCount(val);
                        updateStatus();
                        toastSafe("解锁题数设为 " + val + " 题");
                    } catch (Exception e) {
                        toastSafe("请输入有效数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomLockIntervalDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("0-480（0表示不自动）");
        new AlertDialog.Builder(this)
                .setTitle("自定义锁屏间隔（分钟）")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        if (val < 0) val = 0;
                        if (val > 480) val = 480;
                        pref.setLockIntervalMinutes(val);
                        updateStatus();
                        toastSafe("锁屏间隔设为 " + val + " 分钟");
                    } catch (Exception e) {
                        toastSafe("请输入有效数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomLockDurationDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("0-480（0表示无限）");
        new AlertDialog.Builder(this)
                .setTitle("自定义锁定时长（分钟）")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        if (val < 0) val = 0;
                        if (val > 480) val = 480;
                        pref.setLockDurationMinutes(val);
                        updateStatus();
                        toastSafe(val == 0 ? "锁定时长设为：无限" : "锁定时长设为 " + val + " 分钟");
                    } catch (Exception e) {
                        toastSafe("请输入有效数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomRelockDelayDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("0-30");
        new AlertDialog.Builder(this)
                .setTitle("自定义重新锁屏延迟（秒）")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        if (val < 0) val = 0;
                        if (val > 30) val = 30;
                        pref.setRelockDelaySeconds(val);
                        updateStatus();
                        toastSafe("重新锁屏延迟设为 " + val + " 秒");
                    } catch (Exception e) {
                        toastSafe("请输入有效数字");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void ensureServicesRunning() {
        // 兜底：直接启动 WebConfigServer，不依赖 Service 的异步 onStartCommand
        WebConfigServer server = WebConfigServer.getInstance(getApplicationContext());
        if (!server.isRunning()) {
            Log.w("MainActivity", "Web 监听服务未运行，直接启动 WebConfigServer");
            server.start();
        } else {
            Log.w("MainActivity", "Web 监听服务已运行");
        }
        // 启动前台服务保持常驻
        WebConfigService.ensureRunning(this);
        try {
            startLockService();
        } catch (Exception e) {
            Log.e("MainActivity", "startLockService failed", e);
        }
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
        } catch (Exception e) {
        }
        try {
            if (!isAccessibilityServiceEnabled()) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                toastSafe("请开启「儿童锁屏」无障碍服务以增强锁定效果");
            }
        } catch (Exception e) {
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + KidsLockAccessibilityService.class.getCanonicalName();
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        return enabled.contains(service);
    }

    private void startLockService() {
        Intent intent = new Intent(this, LockService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopLockService() {
        Intent intent = new Intent(this, LockService.class);
        stopService(intent);
    }

    private void toastSafe(String msg) {
        if (isFinishing()) return;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
