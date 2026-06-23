package com.autoreply.messenger.engine;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import com.autoreply.messenger.util.Logger;

public class SwipeEngine {
    public interface Callback { void onDone(boolean ok); }

    /**
     * Swipe trigger reply:
     *   - Bubble trái (isMine=false): swipe → sang phải, từ center bubble
     *   - Bubble phải (isMine=true):  swipe ← sang trái, từ center bubble
     *
     * Từ XML dump: bubble trái [143,332][326,431], bubble phải [875,525][1058,671]
     * Khoảng cần swipe: 250px đủ để Messenger nhận
     * Duration: tối thiểu 60ms — nhanh hơn để giảm latency
     */
    public void swipe(AccessibilityService svc, Rect bounds, boolean isMine, int durationMs, Callback cb) {
        int cy = bounds.centerY();
        int dur  = Math.max(60, durationMs); // Minimum 60ms — Messenger accepts 60ms swipe

        int screenW = 1080;
        if (svc != null && svc.getResources() != null) {
            screenW = svc.getResources().getDisplayMetrics().widthPixels;
        }

        int sx, ex;
        int swipeDist = 900
                ; // Ideal swipe distance in pixels
        if (isMine) {
            sx = bounds.right - 80;
            ex = sx - swipeDist;
        } else {
            sx = bounds.left + 80;
            ex = sx + swipeDist;
        }

        // Clip to screen bounds with safe margins from edges
        sx = Math.min(screenW - 40, Math.max(40, sx));
        ex = Math.min(screenW - 40, Math.max(40, ex));

        Logger.log("swipe " + (isMine ? "←" : "→")
                + " center=(" + bounds.centerX() + "," + cy + ") startX=" + sx + " → endX=" + ex + " dur=" + dur + "ms");

        Path p = new Path();
        p.moveTo(sx, cy);
        p.lineTo(ex, cy);

        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, dur))
                .build();

        boolean ok = svc.dispatchGesture(g, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                Logger.log("swipe ok");
                // Delay tối thiểu 20ms trước khi check reply state
                new Handler(Looper.getMainLooper()).postDelayed(() -> cb.onDone(true), 20);
            }
            @Override public void onCancelled(GestureDescription g) {
                Logger.log("swipe cancelled");
                cb.onDone(false);
            }
        }, null);

        if (!ok) { Logger.error("dispatchGesture=false"); cb.onDone(false); }
    }
}
