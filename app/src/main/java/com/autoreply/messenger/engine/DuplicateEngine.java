package com.autoreply.messenger.engine;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chống xử lý trùng dựa trên sender + nội dung tin nhắn.
 *
 * Logic:
 *   - Mỗi tin được hash bằng sender + normalized(text) — KHÔNG dùng position
 *   - Lý do: positionFromBottom thay đổi khi có tin mới → hash shift → tin cũ
 *     bị coi là mới → reply nhầm
 *   - Trade-off: nếu 2 người gửi cùng nội dung giống hệt, chỉ reply lần đầu
 *     → an toàn hơn so với reply nhầm
 *
 *  Window guard: nếu cùng hash xuất hiện trong vòng 8 giây → skip (tránh double-fire
 *  do nhiều event cùng lúc trên cùng một tin).
 */
public class DuplicateEngine {
    private static final long WINDOW_MS = 8_000;
    private final int maxSize;
    private final LinkedHashMap<String, Long> cache;
    private String lastHash = "";

    public DuplicateEngine(int maxSize) {
        this.maxSize = maxSize;
        cache = new LinkedHashMap<String, Long>(maxSize, 0.75f, false) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Hash = sender + normalized(text).
     * KHÔNG dùng positionFromBottom — vị trí thay đổi khi tin mới đến,
     * gây hash shift → tin cũ bị coi là mới → reply nhầm.
     */
    public String generateHash(String sender, String text) {
        try {
            String normalized = text.trim().toLowerCase()
                    .replaceAll("[\\r\\n]+", "\n")
                    .replaceAll(" +", " ");
            String raw = sender + "||" + normalized;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString().substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString((sender + text).hashCode());
        }
    }

    public boolean isProcessed(String hash) {
        Long t = cache.get(hash);
        if (t == null) return false;
        return (System.currentTimeMillis() - t) < WINDOW_MS;
    }

    public void markProcessed(String hash) {
        cache.put(hash, System.currentTimeMillis());
        lastHash = hash;
    }

    public void setInitialHash(String hash) {
        if (hash != null && !hash.isEmpty()) lastHash = hash;
    }

    public String getLastHash() { return lastHash; }
    public void clear() { cache.clear(); }
}
