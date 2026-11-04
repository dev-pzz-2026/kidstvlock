package com.kidslock.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/**
 * SharedPreferences 封装：管理所有持久化设置和状态。
 * 状态在重启后仍然保留，保证锁屏不会被绕过。
 */
public class PrefManager {

    private static final String PREF_NAME = "kids_lock_prefs";
    private final SharedPreferences prefs;

    // Keys
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_WATCH_LIMIT_MIN = "watch_limit_min";
    private static final String KEY_UNLOCK_COUNT = "unlock_count";
    private static final String KEY_IS_LOCKED = "is_locked";
    private static final String KEY_TIMER_ACTIVE = "timer_active";
    private static final String KEY_TIMER_END_TIME = "timer_end_time";
    
    // 题库配置
    private static final String KEY_CURRENT_BANK_ID = "current_bank_id";
    private static final String KEY_CURRENT_BANK_VERSION = "current_bank_version";
    private static final String KEY_CURRENT_BANK_FILE = "current_bank_file";
    private static final String KEY_FIRST_LAUNCH_SHOWN = "first_launch_shown";

    // Defaults
    private static final boolean DEFAULT_AUTO_START = true;
    private static final int DEFAULT_UNLOCK_COUNT = 3;
    private static final int DEFAULT_WATCH_LIMIT_MIN = 10;
    
    // 锁屏相关配置
    private static final String KEY_LOCK_DURATION_MIN = "lock_duration_min";
    private static final String KEY_LOCK_INTERVAL_MIN = "lock_interval_min";
    private static final String KEY_RELOCK_DELAY_SEC = "relock_delay_sec";
    private static final int DEFAULT_LOCK_DURATION_MIN = 0; // 0表示手动锁屏直到答题解锁
    private static final int DEFAULT_LOCK_INTERVAL_MIN = 3;
    private static final int DEFAULT_RELOCK_DELAY_SEC = 2;

    public PrefManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Auto Start ---
    public boolean isAutoStart() {
        return prefs.getBoolean(KEY_AUTO_START, DEFAULT_AUTO_START);
    }

    public void setAutoStart(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }

    // --- Watch Limit ---
    public int getWatchLimitMinutes() {
        return prefs.getInt(KEY_WATCH_LIMIT_MIN, DEFAULT_WATCH_LIMIT_MIN);
    }

    public void setWatchLimitMinutes(int minutes) {
        prefs.edit().putInt(KEY_WATCH_LIMIT_MIN, minutes).apply();
    }

    // --- Unlock Question Count ---
    public int getUnlockCount() {
        return prefs.getInt(KEY_UNLOCK_COUNT, DEFAULT_UNLOCK_COUNT);
    }

    public void setUnlockCount(int count) {
        prefs.edit().putInt(KEY_UNLOCK_COUNT, count).apply();
    }
    
    // --- Lock Duration (锁定时长，0表示无限直到答题解锁) ---
    public int getLockDurationMinutes() {
        return prefs.getInt(KEY_LOCK_DURATION_MIN, DEFAULT_LOCK_DURATION_MIN);
    }
    
    public void setLockDurationMinutes(int minutes) {
        prefs.edit().putInt(KEY_LOCK_DURATION_MIN, minutes).apply();
    }
    
    // --- Lock Interval (锁屏间隔) ---
    public int getLockIntervalMinutes() {
        return prefs.getInt(KEY_LOCK_INTERVAL_MIN, DEFAULT_LOCK_INTERVAL_MIN);
    }
    
    public void setLockIntervalMinutes(int minutes) {
        prefs.edit().putInt(KEY_LOCK_INTERVAL_MIN, minutes).apply();
    }
    
    // --- Relock Delay (重新锁屏延迟秒数) ---
    public int getRelockDelaySeconds() {
        return prefs.getInt(KEY_RELOCK_DELAY_SEC, DEFAULT_RELOCK_DELAY_SEC);
    }
    
    public void setRelockDelaySeconds(int seconds) {
        prefs.edit().putInt(KEY_RELOCK_DELAY_SEC, seconds).apply();
    }

    // --- Lock State ---
    public boolean isLocked() {
        return prefs.getBoolean(KEY_IS_LOCKED, false);
    }

    public void setLocked(boolean locked) {
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).commit();
    }

    // --- Timer ---
    public boolean isTimerActive() {
        return prefs.getBoolean(KEY_TIMER_ACTIVE, false);
    }

    public void setTimerActive(boolean active) {
        prefs.edit().putBoolean(KEY_TIMER_ACTIVE, active).apply();
    }

    public long getTimerEndTime() {
        return prefs.getLong(KEY_TIMER_END_TIME, 0);
    }

    public void setTimerEndTime(long endTime) {
        prefs.edit().putLong(KEY_TIMER_END_TIME, endTime).apply();
    }

    public long getRemainingMillis() {
        long end = getTimerEndTime();
        long now = System.currentTimeMillis();
        return Math.max(0, end - now);
    }

    // --- Helper: start timer ---
    public void startTimer(int minutes) {
        long endTime = System.currentTimeMillis() + (long) minutes * 60 * 1000;
        prefs.edit()
            .putBoolean(KEY_TIMER_ACTIVE, true)
            .putLong(KEY_TIMER_END_TIME, endTime)
            .putBoolean(KEY_IS_LOCKED, false)
            .commit();
    }

    public void stopTimer() {
        setTimerActive(false);
        setTimerEndTime(0);
    }

    public boolean isTimerExpired() {
        return isTimerActive() && System.currentTimeMillis() >= getTimerEndTime();
    }

    // --- Question Bank Config ---
    public String getCurrentBankId() {
        return prefs.getString(KEY_CURRENT_BANK_ID, "grade1_math");
    }

    public void setCurrentBankId(String bankId) {
        prefs.edit().putString(KEY_CURRENT_BANK_ID, bankId).apply();
    }

    public String getCurrentBankFile() {
        return prefs.getString(KEY_CURRENT_BANK_FILE, "一年级数学.json");
    }

    public void setCurrentBankFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            prefs.edit().remove(KEY_CURRENT_BANK_FILE).apply();
        } else {
            prefs.edit().putString(KEY_CURRENT_BANK_FILE, fileName).apply();
        }
    }

    public int getCurrentBankVersion() {
        return prefs.getInt(KEY_CURRENT_BANK_VERSION, 0);
    }

    public void setCurrentBankVersion(int version) {
        prefs.edit().putInt(KEY_CURRENT_BANK_VERSION, version).apply();
    }



    /**
     * 启用/禁用 HOME launcher alias。
     * 锁屏时启用，让按 HOME 键回到锁屏界面。
     * 解锁时禁用，让按 HOME 键回到正常电视桌面。
     */
    public void setHomeAliasEnabled(Context context, boolean enabled) {
        ComponentName alias = new ComponentName(context, "com.kidslock.app.LockScreenHome");
        PackageManager pm = context.getPackageManager();
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
    }

    // --- First Launch Hint ---
    public boolean isFirstLaunchShown() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH_SHOWN, false);
    }

    public void setFirstLaunchShown(boolean shown) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_SHOWN, shown).apply();
    }
}
