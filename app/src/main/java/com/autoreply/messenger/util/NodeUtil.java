package com.autoreply.messenger.util;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Tất cả selector dựa trên XML dump thực tế:
 * - EditText input: class=EditText, content-desc="Nhập tin nhắn", bounds=[440,2215][859,2261]
 * - Send button: content-desc="Gửi lượt thích", bounds=[959,2190][1080,2261]  (không đổi sau ACTION_SET_TEXT)
 * - Message bubble: content-desc="Sender, text, nhấn đúp để xem..."
 * - Reply state: text="Đang trả lời X", bounds=[33,1183][248,1233]
 * - Group header: content-desc="Test giao đơn, Chi tiết chuỗi bài"
 * - Chat area: y từ 249 đến 2190
 */
public class NodeUtil {

    /** Tìm EditText input box */
    public static AccessibilityNodeInfo findInputBox(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String cls = root.getClassName() != null ? root.getClassName().toString() : "";
        String cd  = root.getContentDescription() != null ? root.getContentDescription().toString() : "";
        if (cls.equals("android.widget.EditText") && cd.equals("Nhập tin nhắn")) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo r = findInputBox(root.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Lấy text hiện tại từ input box Messenger.
     * Trả về null nếu input box không tìm thấy.
     * Trả về "" nếu input box rỗng.
     */
    public static String getInputBoxText(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo input = findInputBox(root);
        if (input == null) return null;
        CharSequence text = input.getText();
        return text != null ? text.toString() : "";
    }

    /** Tìm send button — content-desc="Gửi lượt thích" (không đổi dù có text hay không) */
    public static AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String cd = root.getContentDescription() != null ? root.getContentDescription().toString() : "";
        // Thử "Gửi" trước (một số phiên bản Messenger)
        if (cd.equals("Gửi")) return root;
        if (cd.equals("Gửi lượt thích") && root.isClickable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo r = findSendButton(root.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    /** Kiểm tra group header có chứa groupName không */
    public static boolean hasGroupHeader(AccessibilityNodeInfo root, String groupName) {
        if (root == null || groupName == null || groupName.isEmpty()) return false;
        String cd = root.getContentDescription() != null ? root.getContentDescription().toString() : "";
        if (!cd.isEmpty() && cd.contains(groupName)) return true;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (hasGroupHeader(root.getChild(i), groupName)) return true;
        }
        return false;
    }

    /**
     * Collect tất cả message bubble nodes.
     * Pattern: content-desc chứa "nhấn đúp để xem"
     * Từ XML: "Tú, gà, nhấn đúp để xem ngày giờ gửi/nhận, nhấn đúp và giữ..."
     */
    public static List<AccessibilityNodeInfo> collectMessageNodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        collectMsgRecursive(root, result);
        return result;
    }

    private static void collectMsgRecursive(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        String cd = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        if (cd.contains("nhấn đúp để xem")) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            // Chỉ lấy bubble có kích thước thực (không phải placeholder)
            if (r.height() > 20 && r.width() > 30 && r.top > 0) {
                out.add(node);
            }
            return; // không đệ quy vào con của bubble
        }
        for (int i = 0; i < node.getChildCount(); i++) collectMsgRecursive(node.getChild(i), out);
    }

    /**
     * Parse content-desc thành {sender, text}.
     * Format: "Sender, text content, nhấn đúp để xem..."
     * Ví dụ: "Tú, gà 100k, nhấn đúp để xem ngày giờ gửi/nhận..."
     */
    public static String[] parseMessage(String cd) {
        if (cd == null) return null;
        int idx = cd.indexOf(", nhấn đúp để xem");
        if (idx < 0) return null;
        String payload = cd.substring(0, idx);
        int comma = payload.indexOf(", ");
        if (comma < 0) return null;
        String sender = payload.substring(0, comma).trim();
        String text   = payload.substring(comma + 2).trim();
        if (sender.isEmpty() || text.isEmpty()) return null;
        return new String[]{sender, text};
    }

    /** Kiểm tra reply mode: tìm node có text/content-desc "Đang trả lời" */
    public static boolean isInReplyMode(AccessibilityNodeInfo root) {
        if (root == null) return false;
        String cd  = root.getContentDescription() != null ? root.getContentDescription().toString() : "";
        String txt = root.getText() != null ? root.getText().toString() : "";
        if (cd.contains("Đang trả lời") || txt.contains("Đang trả lời")) return true;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (isInReplyMode(root.getChild(i))) return true;
        }
        return false;
    }

    public static Rect getBounds(AccessibilityNodeInfo node) {
        Rect r = new Rect(); node.getBoundsInScreen(r); return r;
    }

    public static Rect findFreshBounds(AccessibilityNodeInfo root, String sender, String text, Rect fallback) {
        if (root == null) return fallback;
        List<AccessibilityNodeInfo> nodes = collectMessageNodes(root);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            AccessibilityNodeInfo node = nodes.get(i);
            String cd = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
            String[] parsed = parseMessage(cd);
            if (parsed != null && parsed[0].equalsIgnoreCase(sender) && parsed[1].equalsIgnoreCase(text)) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                if (r.height() > 20 && r.width() > 30 && r.top > 0) {
                    return r;
                }
            }
        }
        return fallback;
    }

    /**
     * Extract sender name từ reply panel "Đang trả lời {Sender}".
     * Trả về null nếu không tìm thấy panel.
     */
    public static String getReplyTargetSender(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String result = findReplyTargetSenderRecursive(root);
        return result;
    }

    private static String findReplyTargetSenderRecursive(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String cd  = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String txt = node.getText() != null ? node.getText().toString() : "";

        // content-desc: "Đang trả lời Thương" hoặc text: "Đang trả lời Thương"
        String prefix = "Đang trả lời ";
        if (cd.startsWith(prefix)) {
            return cd.substring(prefix.length()).trim();
        }
        if (txt.startsWith(prefix)) {
            return txt.substring(prefix.length()).trim();
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            String r = findReplyTargetSenderRecursive(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Tìm nút "Hủy trả lời tin nhắn." để cancel reply panel khi phát hiện reply nhầm.
     */
    public static AccessibilityNodeInfo findCancelReplyButton(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String cd = root.getContentDescription() != null ? root.getContentDescription().toString() : "";
        if (cd.contains("Hủy trả lời")) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo r = findCancelReplyButton(root.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Lấy preview text trong reply panel (nội dung tin đang được reply).
     * Dùng để double-check nội dung tin target.
     */
    public static String getReplyPreviewText(AccessibilityNodeInfo root) {
        if (root == null) return null;
        return findReplyPreviewRecursive(root, false);
    }

    private static String findReplyPreviewRecursive(AccessibilityNodeInfo node, boolean foundReplyLabel) {
        if (node == null) return null;
        String cd  = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String txt = node.getText() != null ? node.getText().toString() : "";

        // Nếu đã tìm thấy label "Đang trả lời", sibling tiếp theo chứa preview text
        if (foundReplyLabel && !txt.isEmpty()
                && !txt.contains("Đang trả lời") && !txt.contains("Hủy trả lời")) {
            return txt;
        }

        boolean isReplyLabel = cd.contains("Đang trả lời") || txt.contains("Đang trả lời");

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            String childCd  = child.getContentDescription() != null ? child.getContentDescription().toString() : "";
            String childTxt = child.getText() != null ? child.getText().toString() : "";

            if (childCd.contains("Đang trả lời") || childTxt.contains("Đang trả lời")) {
                // Tìm sibling tiếp theo có text
                for (int j = i + 1; j < node.getChildCount(); j++) {
                    AccessibilityNodeInfo sibling = node.getChild(j);
                    if (sibling == null) continue;
                    String sibTxt = sibling.getText() != null ? sibling.getText().toString() : "";
                    if (!sibTxt.isEmpty() && !sibTxt.contains("Hủy trả lời")) {
                        return sibTxt;
                    }
                    // Tìm trong con của sibling
                    String deep = findTextInChildren(sibling);
                    if (deep != null) return deep;
                }
            }
            // Đệ quy
            String r = findReplyPreviewRecursive(child, isReplyLabel);
            if (r != null) return r;
        }
        return null;
    }

    private static String findTextInChildren(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String txt = node.getText() != null ? node.getText().toString() : "";
        if (!txt.isEmpty() && !txt.contains("Đang trả lời") && !txt.contains("Hủy trả lời")) {
            return txt;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            String r = findTextInChildren(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Single-pass validation: tìm cả inputBox và groupHeader trong 1 lần walk.
     * result[0] = inputBox found, result[1] = groupHeader found
     */
    public static boolean[] validateChatScreen(AccessibilityNodeInfo root, String groupName) {
        boolean[] result = new boolean[]{false, false};
        if (root == null) return result;
        validateChatScreenRecursive(root, groupName, result);
        return result;
    }

    private static void validateChatScreenRecursive(
            AccessibilityNodeInfo node, String groupName, boolean[] result) {
        if (node == null) return;
        if (result[0] && result[1]) return; // early exit — cả 2 đã tìm thấy

        String cls = node.getClassName() != null ? node.getClassName().toString() : "";
        String cd  = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

        if (!result[0] && cls.equals("android.widget.EditText") && cd.equals("Nhập tin nhắn")) {
            result[0] = true;
        }
        if (!result[1] && !cd.isEmpty() && groupName != null && !groupName.isEmpty()
                && cd.contains(groupName)) {
            result[1] = true;
        }
        if (result[0] && result[1]) return;

        for (int i = 0; i < node.getChildCount(); i++) {
            validateChatScreenRecursive(node.getChild(i), groupName, result);
            if (result[0] && result[1]) return;
        }
    }

    /**
     * Single-pass: extract cả sender và preview text từ reply panel.
     * result[0] = sender, result[1] = preview text (có thể null)
     */
    public static String[] getReplyPanelInfo(AccessibilityNodeInfo root) {
        if (root == null) return null;
        String[] result = new String[2]; // [sender, previewText]
        findReplyPanelInfoRecursive(root, false, result);
        return result[0] != null ? result : null;
    }

    private static void findReplyPanelInfoRecursive(AccessibilityNodeInfo node, boolean foundReplyLabel, String[] result) {
        if (node == null) return;

        String cd  = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String txt = node.getText() != null ? node.getText().toString() : "";

        // Nếu đã tìm thấy label "Đang trả lời", sibling tiếp theo chứa preview text
        if (foundReplyLabel && !txt.isEmpty()
                && !txt.contains("Đang trả lời") && !txt.contains("Hủy trả lời")) {
            result[1] = txt;
            return;
        }

        String prefix = "Đang trả lời ";
        boolean isReplyLabel = false;
        if (cd.startsWith(prefix)) {
            result[0] = cd.substring(prefix.length()).trim();
            isReplyLabel = true;
        } else if (txt.startsWith(prefix)) {
            result[0] = txt.substring(prefix.length()).trim();
            isReplyLabel = true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            String childCd  = child.getContentDescription() != null ? child.getContentDescription().toString() : "";
            String childTxt = child.getText() != null ? child.getText().toString() : "";

            if (childCd.contains("Đang trả lời") || childTxt.contains("Đang trả lời")) {
                // Tìm sibling tiếp theo có text
                for (int j = i + 1; j < node.getChildCount(); j++) {
                    AccessibilityNodeInfo sibling = node.getChild(j);
                    if (sibling == null) continue;
                    String sibTxt = sibling.getText() != null ? sibling.getText().toString() : "";
                    if (!sibTxt.isEmpty() && !sibTxt.contains("Hủy trả lời")) {
                        result[1] = sibTxt;
                        break;
                    }
                    // Tìm trong con của sibling
                    String deep = findTextInChildren(sibling);
                    if (deep != null) {
                        result[1] = deep;
                        break;
                    }
                }
            }
            // Đệ quy
            findReplyPanelInfoRecursive(child, isReplyLabel || foundReplyLabel, result);
            if (result[0] != null && result[1] != null) return;
        }
    }
}
