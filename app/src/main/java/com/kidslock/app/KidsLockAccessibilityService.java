package com.kidslock.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class KidsLockAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        PrefManager pref = new PrefManager(this);
        if (pref.isLocked()) {
            String className = event.getClassName() != null ? event.getClassName().toString() : "";
            if (!className.contains("LockScreenActivity")) {
                Intent lockIntent = new Intent(this, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(lockIntent);
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
