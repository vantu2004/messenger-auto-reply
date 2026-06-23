package com.autoreply.messenger.engine;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.autoreply.messenger.model.Message;
import com.autoreply.messenger.util.Logger;
import com.autoreply.messenger.util.NodeUtil;

import java.util.List;

public class MessageEngine {
    private int screenWidth = 1080;
    private static final int INPUT_BAR_Y_FALLBACK = 2100;

    private String myName = "";
    private String replyText = "";

    public void setScreenWidth(int w) { this.screenWidth = w; }
    public void setMyName(String name) { this.myName = name != null ? name.trim() : ""; }
    public void setReplyText(String text) { this.replyText = text != null ? text.trim() : ""; }

    public Message findNewestMessage(AccessibilityNodeInfo root, DuplicateEngine dup) {
        List<AccessibilityNodeInfo> nodes = NodeUtil.collectMessageNodes(root);
        if (nodes.isEmpty()) { Logger.debug("no message nodes"); return null; }

        int inputTop = getInputBarTop(root);
        Logger.debug("nodes=" + nodes.size() + " inputTop=" + inputTop
                + " myName='" + myName + "' replyText='" + replyText + "'");

        for (int i = nodes.size() - 1; i >= 0; i--) {
            AccessibilityNodeInfo node = nodes.get(i);
            String cd = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
            String[] parsed = NodeUtil.parseMessage(cd);
            if (parsed == null) continue;

            String sender = parsed[0];
            String text   = parsed[1];

            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);

            if (bounds.centerY() >= inputTop) {
                Logger.debug("skip input-zone y=" + bounds.centerY());
                continue;
            }

            if (text.contains("đã thu hồi") || text.contains("tin nhắn đã bị xóa")) {
                Logger.debug("skip recalled");
                continue;
            }

            // Phát hiện tin mình bằng 2 cách:
            // 1. Tên: sender == myName hoặc "Bạn"
            boolean isMineByName = sender.equals("Bạn")
                    || (!myName.isEmpty() && sender.equalsIgnoreCase(myName));
            // 2. Nội dung: text == replyText — tin bot vừa gửi (VD: "nhận")
            //    ĐÂY LÀ FILTER QUAN TRỌNG NHẤT
            boolean isBotReply = !replyText.isEmpty()
                    && text.trim().equalsIgnoreCase(replyText.trim());

            boolean isMine = isMineByName || isBotReply;

            String hash = dup.generateHash(sender, text);

            Logger.debug("msg[" + i + "]"
                    + " '" + sender + ": " + text + "'"
                    + " byName=" + isMineByName
                    + " botReply=" + isBotReply + " isMine=" + isMine
                    + " bounds=[" + bounds.left + "," + bounds.top
                    + "][" + bounds.right + "," + bounds.bottom + "]");

            return new Message(sender, text, hash, isMine, bounds);
        }
        return null;
    }

    public String detectMyName(AccessibilityNodeInfo root) {
        // Discarded coordinate-based auto detection to ensure multi-device responsiveness
        return "";
    }

    private int getInputBarTop(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo input = NodeUtil.findInputBox(root);
        if (input == null) return INPUT_BAR_Y_FALLBACK;
        Rect r = new Rect();
        input.getBoundsInScreen(r);
        return r.top - 20;
    }

    public boolean isChatScreenValid(AccessibilityNodeInfo root, String groupName) {
        if (root == null) return false;
        boolean needGroup = groupName != null && !groupName.isEmpty();
        boolean[] valid = NodeUtil.validateChatScreen(root, groupName);
        if (!valid[0]) { Logger.debug("no input box"); return false; }
        if (needGroup && !valid[1]) { Logger.debug("group not found: " + groupName); return false; }
        return true;
    }
}