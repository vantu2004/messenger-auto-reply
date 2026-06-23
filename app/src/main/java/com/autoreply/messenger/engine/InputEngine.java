package com.autoreply.messenger.engine;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import com.autoreply.messenger.util.Logger;

public class InputEngine {
    public boolean setText(AccessibilityNodeInfo et, String text) {
        if (et == null) return false;
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean ok = et.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        Logger.log("setText '" + text + "' → " + ok);
        return ok;
    }
    public void focus(AccessibilityNodeInfo et) {
        if (et != null) et.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
    }
}
