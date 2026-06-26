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
     *
     * ★ OPT: duration 150ms (ngưỡng tối thiểu Messenger nhận diện reply gesture).
     * Thêm điểm dừng (hold) 40ms ở cuối path để Messenger nhận gesture ổn định.
     * Delay callback 80ms — poll loop sẽ bắt kịp nếu panel chưa render xong.
     */
    public void swipe(AccessibilityService svc, Rect bounds, boolean isMine, int durationMs, Callback cb) {
        int cy = bounds.centerY();
        // ★ OPT: tối thiểu 150ms — ngưỡng tối thiểu Messenger nhận reply gesture
        int dur  = Math.max(150, durationMs);

        int screenW = 1080;
        if (svc != null && svc.getResources() != null) {
            screenW = svc.getResources().getDisplayMetrics().widthPixels;
        }

        int sx, ex;
        int swipeDist = 900;
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

        // ★ FIX: Path bao gồm swipe chính + "hold" ở cuối
        // lineTo đến endX → rồi lineTo lại gần đó tạo hiệu ứng dừng (Messenger nhận tốt hơn)
        Path p = new Path();
        p.moveTo(sx, cy);
        p.lineTo(ex, cy);
        // Hold effect: di chuyển cực nhỏ (1px) — giữ Messenger nhận gesture
        // Tổng duration = dur (swipe) + 40ms (hold)
        p.lineTo(ex + 1, cy);

        int totalDur = dur + 40; // 150ms swipe + 40ms hold = 190ms total

        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, totalDur))
                .build();

        boolean ok = svc.dispatchGesture(g, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                Logger.log("swipe ok");
                // ★ OPT: delay 80ms — poll loop sẽ bắt kịp nếu chưa render xong
                new Handler(Looper.getMainLooper()).postDelayed(() -> cb.onDone(true), 80);
            }
            @Override public void onCancelled(GestureDescription g) {
                Logger.log("swipe cancelled");
                cb.onDone(false);
            }
        }, null);

        if (!ok) { Logger.error("dispatchGesture=false"); cb.onDone(false); }
    }
}

