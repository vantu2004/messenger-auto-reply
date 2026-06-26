package com.autoreply.messenger.engine;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

import com.autoreply.messenger.model.Config;
import com.autoreply.messenger.model.Message;
import com.autoreply.messenger.util.Logger;
import com.autoreply.messenger.util.NodeUtil;

public class ReplyEngine {
    public enum State { IDLE, FOUND, REPLYING, SENDING, DONE }

    public interface Listener {
        void onState(State s);
        void onSuccess(String sender, String text, long ms);
        void onFail(String reason);
    }

    private State state = State.IDLE;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final SwipeEngine swiper = new SwipeEngine();
    private final InputEngine input = new InputEngine();
    private Listener listener;

    private AccessibilityService svc;
    private Message pending;
    private Config cfg;
    private long t0;
    private Runnable timeout;

    public void setListener(Listener l) { listener = l; }
    public boolean isIdle() { return state == State.IDLE; }

    public void startReply(AccessibilityService svc, Message msg, Config cfg) {
        if (!isIdle()) { Logger.debug("busy"); return; }
        this.svc = svc; this.pending = msg; this.cfg = cfg;
        t0 = System.currentTimeMillis();
        setState(State.FOUND);
        Logger.log("found: " + msg.sender + " → " + msg.text
                + " isMine=" + msg.isMine
                + " bounds=[" + msg.bubbleBounds.left + "," + msg.bubbleBounds.top
                + "][" + msg.bubbleBounds.right + "," + msg.bubbleBounds.bottom + "]");
        doSwipe(0);
    }

    private void doSwipe(int attempt) {
        setState(State.REPLYING);

        // ★ FIX: Re-fetch fresh bounds trước mỗi lần retry
        // Vì bubble có thể đã shift position khi có tin mới
        if (attempt > 0) {
            AccessibilityNodeInfo freshRoot = svc.getRootInActiveWindow();
            if (freshRoot != null) {
                android.graphics.Rect freshBounds = com.autoreply.messenger.util.NodeUtil.findFreshBounds(
                        freshRoot, pending.sender, pending.text, pending.bubbleBounds);
                if (freshBounds != pending.bubbleBounds) {
                    Logger.log("retry#" + attempt + " refreshed bounds=["
                            + freshBounds.left + "," + freshBounds.top
                            + "][" + freshBounds.right + "," + freshBounds.bottom + "]");
                    pending.bubbleBounds.set(freshBounds);
                }
            }
        }

        swiper.swipe(svc, pending.bubbleBounds, pending.isMine, cfg.gestureDuration, ok -> {
            if (!ok) {
                // ★ OPT: retry delay giảm 200→100ms
                if (attempt < 2) { h.postDelayed(() -> doSwipe(attempt + 1), 100); }
                else fail("swipe_failed");
                return;
            }
            armTimeout();
            checkReply(0, attempt);
        });
    }

    /**
     * Poll "Đang trả lời" — check ngay, sau đó mỗi 30ms.
     * ★ OPT: 50 checks × 30ms = 1.5s (đủ dài để bắt reply state)
     * Nếu hết 50 checks mà không thấy reply state → retry swipe (tối đa 3 lần)
     */
    private void checkReply(int attempt, int swipeAttempt) {
        h.postDelayed(() -> {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root == null) { cancelTimeout(); fail("root_null"); return; }

            boolean inReply = NodeUtil.isInReplyMode(root);
            Logger.debug("checkReply #" + attempt + " in=" + inReply
                    + " t=" + (System.currentTimeMillis() - t0) + "ms");

            if (inReply) {
                cancelTimeout();
                // ★ VERIFY: Kiểm tra đang reply đúng tin trước khi gửi
                if (!verifyReplyTarget(root, pending)) {
                    cancelReplyPanel(root);
                    fail("wrong_reply_target");
                    return;
                }
                doInput(root);
            } else if (attempt < 50) { // ★ OPT: 50 checks × 30ms = 1.5s
                checkReply(attempt + 1, swipeAttempt);
            } else {
                cancelTimeout();
                // ★ FIX: Retry swipe nếu chưa hết số lần thử
                // Trường hợp swipe "ok" nhưng Messenger không nhận gesture
                if (swipeAttempt < 2) {
                    Logger.log("no reply state after " + attempt + " checks → retry swipe #" + (swipeAttempt + 1));
                    h.postDelayed(() -> doSwipe(swipeAttempt + 1), 100);
                } else {
                    Logger.error("no reply state after " + attempt + " checks (all " + (swipeAttempt + 1) + " swipe attempts exhausted)");
                    fail("no_reply_state");
                }
            }
        }, attempt == 0 ? 0 : 30); // ★ OPT: interval 30ms (nhanh hơn, tổng vẫn 1.5s)
    }

    /**
     * Verify reply panel đang trả lời đúng tin target.
     * Check sender name từ "Đang trả lời {Sender}" và preview text.
     * Trả về false nếu không khớp → cần hủy reply.
     */
    private boolean verifyReplyTarget(AccessibilityNodeInfo root, Message target) {
        if (target == null) return false;

        String[] panelInfo = NodeUtil.getReplyPanelInfo(root);
        if (panelInfo == null) {
            Logger.error("verify: cannot extract reply panel info");
            return false;
        }

        String replyingSender = panelInfo[0];
        if (replyingSender == null) {
            Logger.error("verify: cannot extract reply sender");
            return false;
        }

        if (!replyingSender.equalsIgnoreCase(target.sender)) {
            Logger.error("verify: sender mismatch panel='" + replyingSender
                    + "' expected='" + target.sender + "'");
            return false;
        }

        // Check preview text (optional — tin dài có thể bị truncate)
        String previewText = panelInfo[1];
        if (previewText != null && !previewText.isEmpty()) {
            String targetNorm = target.text.trim().toLowerCase();
            String previewNorm = previewText.trim().toLowerCase();
            int compareLen = Math.min(20, Math.min(targetNorm.length(), previewNorm.length()));
            if (compareLen > 0
                    && !targetNorm.startsWith(previewNorm.substring(0, Math.min(compareLen, previewNorm.length())))
                    && !previewNorm.startsWith(targetNorm.substring(0, compareLen))) {
                Logger.error("verify: preview mismatch '" + previewNorm + "' vs '" + targetNorm + "'");
                return false;
            }
        }

        Logger.log("verify: reply target OK sender='" + replyingSender + "'");
        return true;
    }

    /**
     * Hủy reply panel bằng cách click "Hủy trả lời tin nhắn."
     */
    private void cancelReplyPanel(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo cancelBtn = NodeUtil.findCancelReplyButton(root);
        if (cancelBtn != null) {
            cancelBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Logger.log("cancelled wrong reply panel");
        } else {
            Logger.error("cancel button not found");
        }
    }

    private void doInput(AccessibilityNodeInfo root) {
        setState(State.SENDING);
        AccessibilityNodeInfo et = NodeUtil.findInputBox(root);
        if (et == null) { fail("no_input"); return; }
        input.focus(et);
        if (!input.setText(et, cfg.replyText)) { fail("set_text_fail"); return; }
        // Delay 15ms rồi click send — tối ưu từ 50ms xuống 15ms
        h.postDelayed(this::doSend, 15);
    }

    private void doSend() {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) { fail("root_null_send"); return; }
        AccessibilityNodeInfo btn = NodeUtil.findSendButton(root);
        if (btn == null) { fail("no_send_btn"); return; }
        boolean ok = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        long ms = System.currentTimeMillis() - t0;
        Logger.log("send=" + ok + " latency=" + ms + "ms");
        setState(State.DONE);
        if (listener != null) listener.onSuccess(pending.sender, pending.text, ms);
        reset();
    }

    private void fail(String r) {
        Logger.error("fail: " + r);
        if (listener != null) listener.onFail(r);
        reset();
    }

    private void armTimeout() {
        timeout = () -> { if (state == State.REPLYING) { Logger.error("timeout"); fail("timeout"); } };
        // ★ OPT: timeout 5s (accommodate 3 retry, mỗi retry ~1.5s poll)
        h.postDelayed(timeout, 5000);
    }

    private void cancelTimeout() {
        if (timeout != null) { h.removeCallbacks(timeout); timeout = null; }
    }

    private void setState(State s) {
        state = s;
        if (listener != null) listener.onState(s);
    }

    public void reset() {
        cancelTimeout();
        h.removeCallbacksAndMessages(null);
        setState(State.IDLE);
        pending = null; cfg = null;
    }

    public void forceReset() {
        h.removeCallbacksAndMessages(null);
        state = State.IDLE; pending = null; cfg = null;
    }
}
