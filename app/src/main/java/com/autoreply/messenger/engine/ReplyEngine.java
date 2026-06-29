package com.autoreply.messenger.engine;

import android.accessibilityservice.AccessibilityService;
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
    private Listener listener;

    private AccessibilityService svc;
    private Message pending;
    private Config cfg;
    private long t0;

    public void setListener(Listener l) { listener = l; }
    public boolean isIdle() { return state == State.IDLE; }
    public Message getPending() { return pending; }

    public void startReply(AccessibilityService svc, Message msg, Config cfg) {
        if (!isIdle()) return;
        this.svc = svc; this.pending = msg; this.cfg = cfg;
        t0 = System.currentTimeMillis();
        setState(State.FOUND);
        Logger.log("Detected matching msg: " + msg.sender + " -> " + msg.text + ". Waiting for user to swipe & type...");
        setState(State.REPLYING);
    }

    /**
     * Called from MessengerAccessibilityService on every accessibility event
     * when state is not IDLE.
     */
    public void onEvent(AccessibilityNodeInfo root) {
        if (state != State.REPLYING || pending == null || root == null) return;

        // 1. Reset nếu đã chờ quá lâu (ví dụ 30 giây) để tránh bị kẹt trạng thái khi người dùng bỏ qua
        if (System.currentTimeMillis() - t0 > 30000) {
            Logger.log("Timeout waiting for user swipe/type (30s). Resetting to IDLE.");
            reset();
            return;
        }

        // 2. Kiểm tra xem có đang ở chế độ reply không
        boolean inReply = NodeUtil.isInReplyMode(root);
        if (!inReply) {
            return;
        }

        // 3. Kiểm tra thông tin trong panel đang trả lời
        String[] panelInfo = NodeUtil.getReplyPanelInfo(root);
        if (panelInfo == null || panelInfo[0] == null) return;

        String replyingSender = panelInfo[0];
        if (!replyingSender.equalsIgnoreCase(pending.sender)) {
            // Người dùng đã swipe trả lời tin nhắn của người khác -> Reset để không click nhầm
            Logger.log("User swiped a different sender (" + replyingSender + " vs expected " + pending.sender + "). Resetting to IDLE.");
            reset();
            return;
        }

        // Kiểm tra preview text (nếu lệch quá nhiều thì reset)
        String previewText = panelInfo[1];
        if (previewText != null && !previewText.isEmpty()) {
            String targetNorm = pending.text.trim().toLowerCase();
            String previewNorm = previewText.trim().toLowerCase();
            int compareLen = Math.min(20, Math.min(targetNorm.length(), previewNorm.length()));
            if (compareLen > 0
                    && !targetNorm.startsWith(previewNorm.substring(0, Math.min(compareLen, previewNorm.length())))
                    && !previewNorm.startsWith(targetNorm.substring(0, compareLen))) {
                Logger.log("User swiped a different message text. Resetting to IDLE.");
                reset();
                return;
            }
        }

        // 4. Kiểm tra xem ô nhập liệu đã được điền text chưa
        AccessibilityNodeInfo et = NodeUtil.findInputBox(root);
        if (et == null) return;

        CharSequence txt = et.getText();
        if (txt == null || txt.toString().trim().isEmpty()) {
            // Chưa có text hoặc text trống -> tiếp tục đợi
            return;
        }

        // Đủ điều kiện: Đã swipe đúng tin và đã có nội dung -> Click gửi tự động!
        Logger.log("Conditions met (swiped & text entered). Auto-clicking Send...");
        setState(State.SENDING);
        doSend(root);
    }

    private void doSend(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo btn = NodeUtil.findSendButton(root);
        if (btn == null) {
            Logger.error("Send button not found");
            setState(State.REPLYING); // Quay lại đợi
            return;
        }
        boolean ok = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        long ms = System.currentTimeMillis() - t0;
        Logger.log("Send clicked=" + ok + " latency=" + ms + "ms");
        setState(State.DONE);
        if (listener != null) {
            listener.onSuccess(pending.sender, pending.text, ms);
        }
        reset();
    }

    public void reset() {
        setState(State.IDLE);
        pending = null;
        cfg = null;
    }

    public void forceReset() {
        state = State.IDLE;
        pending = null;
        cfg = null;
    }

    private void setState(State s) {
        state = s;
        if (listener != null) listener.onState(s);
    }
}
