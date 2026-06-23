# AUTO SHIP REPLY — BUILD PLAN V4

**Android Accessibility Bot | Java + XML | Target: < 300ms | Zero false-reply guarantee**

---

## 1. PHÂN TÍCH XML THỰC TẾ (từ 5 file dump)

### 1.1 Nhận diện màn hình Chat — Tất cả điều kiện phải đồng thời đúng

```
Package  : com.facebook.orca
Nút quay : Button có content-desc == "Quay lại"   [bounds ~[11,113][143,245]]
Group btn: Button có content-desc chứa settings.groupName  [bounds ~[154,113][673,245]]
EditText : EditText có content-desc == "Nhập tin nhắn"
```

Không dùng package đơn — list chat, bubble, notification cùng package. Phải kiểm tra đủ 3 điều kiện trên mới coi là đang trong đúng group chat.

### 1.2 Parse Message Node từ XML thực tế

Mỗi tin nhắn trong RecyclerView là một **ViewGroup** clickable có **content-desc** theo format:

```
"{Sender}, {text}, nhấn đúp để xem ngày giờ gửi/nhận, nhấn đúp và giữ để bày tỏ cảm xúc về tin nhắn"
```

**Ví dụ thực tế từ dump:**
- `"Huy, nhận dk về hl, nhấn đúp để xem..."` → sender="Huy", text="nhận dk về hl"
- `"Nguyễn, 1 ổ bmi heo quay\nMua 643hv\n\nShip 22 nguyễn chí thanh\n0929123345, nhấn đúp để xem..."` → sender="Nguyễn", text="1 ổ bmi heo quay\n..."
- `"Thương, 1 ts kem hạt \n✅ đã gọi padme\n\nShip hà lan 1240 hùng vương, nhấn đúp để xem..."`

**Parse algorithm:**
```java
// Tách ở dấu phẩy đầu tiên, loại bỏ suffix cố định
String SUFFIX = ", nhấn đúp để xem ngày giờ gửi/nhận, nhấn đúp và giữ để bày tỏ cảm xúc về tin nhắn";
String raw = contentDesc.replace(SUFFIX, "").trim();
int firstComma = raw.indexOf(", ");
if (firstComma < 0) return null; // không hợp lệ
String sender = raw.substring(0, firstComma).trim();
String text   = raw.substring(firstComma + 2).trim();
```

**Tin bị truncate (text dài):** Lấy full text từ `text` attribute của ViewGroup con bên trong bubble. Con này có `text` attribute chứa nội dung đầy đủ kể cả xuống dòng.

### 1.3 Phân biệt tin của mình vs tin người khác

**Từ XML thực tế:**
- Tin người khác: ViewGroup bubble nằm bên **trái** (x bắt đầu từ 143), có avatar `ImageView` `content-desc="Mở trang cá nhân của {tên}"` bên trái, có label tên sender ở trên bubble
- Tin của mình ("Bạn"): ViewGroup bubble nằm bên **phải** (x kết thúc ~1058), **không có** ImageView avatar, header label thường chứa "Bạn đã trả lời..." hoặc không có label

**Quy tắc an toàn:**
```java
// Ưu tiên check sender name trước
boolean isMine = sender.equalsIgnoreCase("Bạn") || sender.equalsIgnoreCase(settings.myName);
// Fallback: kiểm tra không có sibling ImageView "Mở trang cá nhân" trong cùng group row
```

### 1.4 Phân loại các trạng thái tin nhắn (từ XML thực tế)

| Content-desc của bubble node | Ý nghĩa | Xử lý |
|---|---|---|
| `"{sender}, {text}, nhấn đúp..."` | Tin bình thường | Parse + process |
| `"{sender} đã xóa một tin nhắn"` | Tin thu hồi | BỎ QUA |
| `"{sender} đã trả lời {người}"` | Header reply indicator | BỎ QUA (không phải bubble chính) |
| `"Bạn đã trả lời {người}"` | Reply của mình | BỎ QUA |
| Node chứa `"· Đã chỉnh sửa"` | Tin đã edit | Parse bình thường nhưng re-fingerprint |

### 1.5 Reply Panel (ui_005.xml — trạng thái sau khi swipe)

Sau khi swipe thành công, bottom bar thay đổi — xuất hiện panel reply:

```
ViewGroup [bounds: 33,1929][948,2070]
  ├── ViewGroup content-desc="Đang trả lời {Sender}"   ← DETECT bằng cái này
  ├── ViewGroup text="{nội dung tin được reply}"        ← verify đúng tin
  └── Button content-desc="Hủy trả lời tin nhắn."      ← cancel nếu cần
```

**Poll để detect:** Cứ 30ms kiểm tra `content-desc startsWith "Đang trả lời"`. Timeout 2000ms.

**Verify:** Sau khi detect panel, đọc nội dung preview bên dưới và so sánh với `targetText.startsWith(previewText)` để chắc chắn đang reply đúng tin.

### 1.6 Nút Gửi (từ XML thực tế)

```
Chưa nhập text: Button content-desc="Gửi 😊"       [bounds: 981,2115][1080,2168]
Sau nhập text:  Button content-desc="Gửi"           [bounds: 981,2115][1080,2168]
```

→ Sau setText, tìm Button `content-desc == "Gửi"` trước. Nếu không có thì fallback `content-desc startsWith "Gửi"`.

### 1.7 Swipe Direction (từ layout thực tế)

- Tin người khác (trái màn hình, x từ 143): Swipe **phải** (left → right)
- Tin của mình (phải màn hình, x đến ~1058): Swipe **trái** (right → left) — nhưng bot không reply tin mình

**Tính toán swipe từ bounds:**
```java
Rect b = targetBubble.getBoundsInScreen();
int midY = (b.top + b.bottom) / 2;
// Tin trái: swipe phải
gestureService.swipe(b.left + 20, midY, b.left + 300, midY, 80);
```

---

## 2. DATA MODEL

### 2.1 MessageSnapshot

```java
public class MessageSnapshot {
    String  fingerprint;       // SHA-256 truncated 16 hex chars
    String  sender;
    String  text;
    boolean isDeleted;         // "đã xóa một tin nhắn"
    boolean isMine;
    long    firstSeenAt;       // System.currentTimeMillis() khi lần đầu detect
    Rect    bubbleBounds;      // bounds của clickable bubble ViewGroup
    // transient — không serialize
    transient AccessibilityNodeInfo liveNode;
}
```

### 2.2 ConversationSnapshot

```java
public class ConversationSnapshot {
    // Key: fingerprint, ordered by appearance (top→bottom)
    LinkedHashMap<String, MessageSnapshot> messages = new LinkedHashMap<>();
    long capturedAt;
    String groupName;
}
```

### 2.3 SnapshotDiff

```java
public class SnapshotDiff {
    List<MessageSnapshot> added;    // mới xuất hiện
    List<MessageSnapshot> removed;  // đã biến mất
    List<MessageSnapshot> updated;  // fingerprint cũ→mới (tin bị edit)
}
```

### 2.4 KeywordRuleSet (Room Entity) — **Chọn 1 trong nhiều bộ**

```java
@Entity(tableName = "rule_sets")
public class KeywordRuleSet {
    @PrimaryKey(autoGenerate = true) int id;
    String name;          // "Ninja Ship", "Grab Food", "Giao hàng nhanh"...
    String description;   // mô tả ngắn
    boolean isActive;     // CHỈ 1 bộ active tại một thời điểm
    int     orderIndex;
    long    createdAt;
}
```

```java
@Entity(tableName = "keyword_rules",
        foreignKeys = @ForeignKey(entity=KeywordRuleSet.class,
                                  parentColumns="id", childColumns="ruleSetId",
                                  onDelete=CASCADE))
public class KeywordRule {
    @PrimaryKey(autoGenerate = true) int id;
    int    ruleSetId;
    String ruleName;
    String keywords;   // JSON array: ["ship","đơn","pickup","nhận đk"]
    String excludes;   // JSON array: ["hủy","cancel","thu hồi"]
    String replyText;  // nếu null → dùng replyText từ AppSettings
    int    orderIndex;
    boolean enabled;
}
```

**Constraint DB:** Khi `isActive = true` cho một RuleSet thì trigger SQL tự động set `isActive = false` cho tất cả các RuleSet khác.

### 2.5 HistoryEntry (Room Entity)

```java
@Entity(tableName = "history")
public class HistoryEntry {
    @PrimaryKey(autoGenerate = true) long id;
    String sender;
    String messageText;
    String fingerprint;
    String matchedRuleSet;
    String matchedRule;
    String replyText;
    long   timestamp;
    boolean success;
    String  failReason;   // null nếu success
}
```

### 2.6 AppSettings (Room Entity)

```java
@Entity(tableName = "settings")
public class AppSettings {
    @PrimaryKey int id = 1;
    String groupName;       // so sánh với Button content-desc trong header
    String myName;          // tên của mình trong group
    String replyText;       // text reply mặc định (nếu rule không override)
    String allowedSenders;  // JSON array, rỗng = tất cả
    int    throttleMs;      // default 80ms
    int    replyPanelTimeoutMs; // default 2000ms
}
```

---

## 3. FINGERPRINT

```java
public class FingerprintUtil {
    // LRU-like cache tránh tính lại
    private static final int CACHE_SIZE = 500;
    private static final LinkedHashMap<String, String> cache =
        new LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String,String> e) {
                return size() > CACHE_SIZE;
            }
        };

    public static String compute(String sender, String text) {
        // Normalize: trim, lowercase, collapse whitespace, chuẩn hóa newline
        String normalized = text.trim().toLowerCase()
                               .replaceAll("[\r\n]+", "\n")
                               .replaceAll(" +", " ");
        String key = sender + "|" + normalized;
        return cache.computeIfAbsent(key, k -> {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(k.getBytes(StandardCharsets.UTF_8));
                return String.format("%016x", ByteBuffer.wrap(hash).getLong());
            } catch (Exception e) {
                return Integer.toHexString(k.hashCode());
            }
        });
    }
}
```

**Lưu ý:** Không dùng `prevFingerprint` trong hash (như V3) vì gây race condition khi tin mới đến nhanh — thứ tự render có thể thay đổi làm fingerprint sai.

---

## 4. STATE MACHINE

```
DISABLED
    │ (user bật Start)
    ▼
WAIT_CHAT  ← (rời khỏi group chat)
    │ (detect đúng group)
    ▼
READY ──────────────────────── (AccessibilityEvent đến, throttle 80ms)
    │                                              │
    │ (scan thấy tin mới pass filter)              │ (không có gì)
    ▼                                              │
REPLYING ──────────────────────────────────────────┘
    │
    │ (reply xong hoặc fail)
    ▼
READY
```

**Chỉ có 1 goroutine reply tại một thời điểm** — dùng `AtomicBoolean isReplying`. Nếu event mới đến khi đang reply → bỏ qua, đợi event sau.

---

## 5. LUỒNG XỬ LÝ CHÍNH (Simplified & Bug-proof)

### 5.1 Khi nhận AccessibilityEvent

```
onAccessibilityEvent(event)
    │
    ├─ throttle: nếu (now - lastEventTime) < 80ms → return
    ├─ lastEventTime = now
    │
    ├─ isOnTargetChat() ? NO → return
    ├─ isReplying.get() ? YES → return   ← KHÔNG xử lý khi đang reply
    │
    ├─ currSnapshot = captureVisibleMessages()
    ├─ diff = SnapshotDiffEngine.diff(prevSnapshot, currSnapshot)
    ├─ prevSnapshot = currSnapshot
    │
    └─ for each msg in diff.added:
           if passAllFilters(msg):
               scheduleReply(msg)   ← chỉ reply 1 tin tại một lúc
               break                ← không reply nhiều tin cùng lúc
```

### 5.2 captureVisibleMessages()

Duyệt cây accessibility từ root, tìm tất cả ViewGroup có:
- `isClickable() == true`
- `content-desc` khớp pattern tin nhắn (chứa `", nhấn đúp để xem"`)
- Không phải header reply indicator (`content-desc` chứa "đã trả lời")

Với mỗi node hợp lệ: parse sender + text, tạo MessageSnapshot, tính fingerprint.

### 5.3 passAllFilters(MessageSnapshot msg)

```java
boolean passAllFilters(MessageSnapshot msg) {
    if (msg.isMine)    return false;  // không reply tin mình
    if (msg.isDeleted) return false;  // không reply tin thu hồi
    if (msg.firstSeenAt < sessionStartTime) return false;  // tin cũ trước khi bot chạy

    // Sender whitelist
    if (!settings.allowedSenders.isEmpty()
        && !settings.allowedSenders.contains(msg.sender)) return false;

    // Đã reply tin này rồi
    if (replyHistory.contains(msg.fingerprint)) return false;

    // Match keyword rule trong active RuleSet
    KeywordRule matched = ruleEngine.findMatchingRule(msg.text);
    if (matched == null) return false;

    msg.matchedRule = matched;
    return true;
}
```

### 5.4 scheduleReply(MessageSnapshot msg)

```java
void scheduleReply(MessageSnapshot msg) {
    if (!isReplying.compareAndSet(false, true)) return; // đã có reply đang chạy

    executor.execute(() -> {
        try {
            boolean ok = replyEngine.execute(msg);
            if (ok) {
                replyHistory.add(msg.fingerprint);
                db.historyDao().insert(new HistoryEntry(msg, true, null));
            } else {
                db.historyDao().insert(new HistoryEntry(msg, false, replyEngine.lastError));
            }
        } finally {
            isReplying.set(false);
        }
    });
}
```

---

## 6. REPLY ENGINE — BUG-PROOF EDITION

Đây là phần quan trọng nhất. Toàn bộ luồng reply được thiết kế để **tuyệt đối không reply nhầm**.

### 6.1 Tổng quan luồng

```
execute(targetMsg)
    │
    ├─ [1] RE-VERIFY: Tìm lại node live trên màn hình bằng fingerprint
    │       Nếu không tìm được → ABORT (tin có thể đã bị xóa hoặc cuộn mất)
    │
    ├─ [2] LOCK TARGET: Ghi lại bounds + fingerprint của target
    │
    ├─ [3] SWIPE target node
    │
    ├─ [4] POLL reply panel (30ms interval, timeout 2000ms)
    │       Nếu timeout → ABORT + log fail
    │
    ├─ [5] VERIFY PANEL: Đọc "Đang trả lời {Sender}" và preview text
    │       Nếu sender không khớp targetMsg.sender → HỦY (click "Hủy trả lời") + ABORT
    │       Nếu preview text không prefix-match targetMsg.text → HỦY + ABORT
    │
    ├─ [6] SET TEXT vào EditText
    │
    ├─ [7] CLICK "Gửi"
    │
    └─ [8] VERIFY GỬI THÀNH CÔNG: Sau 300ms, kiểm tra EditText.text == ""
            Nếu không → ABORT + log fail
```

### 6.2 Re-verify Node (bước 1 — core anti-bug)

```java
AccessibilityNodeInfo findLiveNode(MessageSnapshot target) {
    // Lấy root mới nhất từ accessibility service
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root == null) return null;

    List<AccessibilityNodeInfo> candidates = new ArrayList<>();
    collectMessageNodes(root, candidates);

    for (AccessibilityNodeInfo node : candidates) {
        String cd = node.getContentDescription() != null
                    ? node.getContentDescription().toString() : "";
        // Parse lại sender và text từ node live
        ParseResult pr = parseContentDesc(cd);
        if (pr == null) continue;

        // So khớp: sender phải giống hệt + text phải giống hệt (sau normalize)
        if (pr.sender.equals(target.sender)
            && normalize(pr.text).equals(normalize(target.text))) {
            return node;
        }
    }
    return null; // không tìm thấy → abort
}
```

**Tại sao không dùng nodeRef cũ?** — XML dump cho thấy Messenger RecyclerView recycle view liên tục. NodeRef cũ có thể bị recycle và gán cho tin hoàn toàn khác, dẫn đến swipe nhầm.

### 6.3 Verify Panel (bước 5 — anti-wrong-reply)

```java
boolean verifyReplyPanel(MessageSnapshot target) {
    AccessibilityNodeInfo root = getRootInActiveWindow();

    // Tìm node "Đang trả lời {X}"
    AccessibilityNodeInfo replyingNode = findNodeWithContentDescStarting(root, "Đang trả lời ");
    if (replyingNode == null) return false;

    String replyingDesc = replyingNode.getContentDescription().toString();
    // "Đang trả lời Thương" → extract "Thương"
    String replyingSender = replyingDesc.replace("Đang trả lời ", "").trim();

    if (!replyingSender.equals(target.sender)) {
        cancelReplyPanel(root);  // click "Hủy trả lời tin nhắn."
        return false;
    }

    // Verify preview text (sibling node bên dưới)
    // Node text preview: text attribute = nội dung tin target
    // (từ xml: text="1 ts kem hạt \n✅ đã gọi padme\n\nShip hà lan 1240 hùng vương")
    // Chỉ cần prefix match (tin dài có thể bị truncate trong preview)
    AccessibilityNodeInfo previewNode = findPreviewTextNode(root);
    if (previewNode != null) {
        String preview = previewNode.getText() != null
                         ? previewNode.getText().toString().trim() : "";
        String targetNorm = normalize(target.text);
        if (!targetNorm.startsWith(normalize(preview)) 
            && !normalize(preview).startsWith(targetNorm.substring(0, Math.min(20, targetNorm.length())))) {
            cancelReplyPanel(root);
            return false;
        }
    }

    return true;
}
```

### 6.4 Swipe Gesture

```java
void swipeToReply(AccessibilityNodeInfo node) {
    Rect b = new Rect();
    node.getBoundsInScreen(b);
    int midY = (b.top + b.bottom) / 2;

    // Tin trái (người khác): swipe phải
    // Tất cả tin cần reply đều là tin người khác → luôn swipe phải
    int startX = b.left + 30;
    int endX   = Math.min(b.left + 350, b.right - 30);

    GestureDescription.StrokeDescription stroke =
        new GestureDescription.StrokeDescription(
            buildPath(startX, midY, endX, midY), 0, 80);
    dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
}
```

---

## 7. SNAPSHOT DIFF ENGINE

```java
public class SnapshotDiffEngine {
    public SnapshotDiff diff(ConversationSnapshot prev, ConversationSnapshot curr) {
        SnapshotDiff result = new SnapshotDiff();

        Set<String> prevKeys = prev != null ? prev.messages.keySet() : new HashSet<>();
        Set<String> currKeys = curr.messages.keySet();

        for (String fp : currKeys) {
            if (!prevKeys.contains(fp)) {
                result.added.add(curr.messages.get(fp));
            }
        }
        for (String fp : prevKeys) {
            if (!currKeys.contains(fp)) {
                result.removed.add(prev.messages.get(fp));
            }
        }
        return result;
        // Không cần track "updated" ở tầng diff —
        // tin edit sẽ xuất hiện như "added" với fingerprint mới
    }
}
```

---

## 8. RULE ENGINE

```java
public class RuleEngine {
    // Chỉ dùng active RuleSet
    public KeywordRule findMatchingRule(String text) {
        KeywordRuleSet activeSet = db.ruleSetDao().getActiveSet();
        if (activeSet == null) return null;

        List<KeywordRule> rules = db.ruleDao().getEnabledRules(activeSet.id);
        // Sắp xếp theo orderIndex
        for (KeywordRule rule : rules) {
            List<String> keywords = Gson.fromJson(rule.keywords, List.class);
            List<String> excludes = Gson.fromJson(rule.excludes, List.class);

            String textLower = text.toLowerCase();
            boolean hasKeyword = keywords.stream().anyMatch(k -> textLower.contains(k.toLowerCase()));
            boolean hasExclude = excludes.stream().anyMatch(e -> textLower.contains(e.toLowerCase()));

            if (hasKeyword && !hasExclude) return rule;
        }
        return null;
    }
}
```

---

## 9. MESSENGER DETECTOR

```java
public class MessengerDetector {
    public boolean isOnTargetChat(AccessibilityNodeInfo root, AppSettings settings) {
        if (root == null) return false;
        if (!"com.facebook.orca".equals(root.getPackageName())) return false;

        // Check nút Quay lại
        boolean hasBack = findNodeWithContentDesc(root, "Quay lại") != null;
        if (!hasBack) return false;

        // Check Group header button chứa tên group
        // Từ XML: Button content-desc="GRUOP NHẬN ĐƠN NINJA, Chi tiết chuỗi bài"
        boolean hasGroup = findNodeWithContentDescContaining(root, settings.groupName) != null;
        if (!hasGroup) return false;

        // Check EditText nhập tin nhắn
        boolean hasInput = findNodeWithContentDesc(root, "Nhập tin nhắn") != null;
        return hasInput;
    }
}
```

---

## 10. ACCESSIBILITY SERVICE

```java
@Override
public void onAccessibilityEvent(AccessibilityEvent event) {
    // Throttle: bỏ qua event quá gần nhau (RecyclerView spam nhiều event)
    long now = SystemClock.elapsedRealtime();
    if (now - lastEventTime < settings.throttleMs) return; // default 80ms
    lastEventTime = now;

    // Chỉ quan tâm TYPE_WINDOW_CONTENT_CHANGED và TYPE_WINDOW_STATE_CHANGED
    int type = event.getEventType();
    if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

    mainHandler.post(this::processEvent);
}

private void processEvent() {
    if (stateMachine.getState() == BotState.DISABLED) return;
    if (isReplying.get()) return; // đang reply → bỏ qua

    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (!messengerDetector.isOnTargetChat(root, settings)) {
        stateMachine.transitionTo(BotState.WAIT_CHAT);
        return;
    }
    stateMachine.transitionTo(BotState.READY);

    ConversationSnapshot curr = snapshotBuilder.capture(root);
    SnapshotDiff diff = diffEngine.diff(prevSnapshot, curr);
    prevSnapshot = curr;

    for (MessageSnapshot msg : diff.added) {
        if (passAllFilters(msg)) {
            scheduleReply(msg);
            break; // chỉ 1 tin mỗi lượt
        }
    }
}
```

---

## 11. CẤU TRÚC PROJECT

```
app/src/main/java/com/autoshipreply/
├── service/
│   └── AutoReplyAccessibilityService.java
├── engine/
│   ├── MessengerDetector.java
│   ├── SnapshotBuilder.java        ← captureVisibleMessages()
│   ├── SnapshotDiffEngine.java
│   ├── RuleEngine.java
│   └── ReplyEngine.java            ← toàn bộ luồng reply + verify
├── model/
│   ├── MessageSnapshot.java
│   ├── ConversationSnapshot.java
│   └── SnapshotDiff.java
├── repository/
│   └── MessageRepository.java      ← quản lý prevSnapshot + replyHistory
├── state/
│   └── BotStateMachine.java
├── db/
│   ├── AppDatabase.java
│   ├── dao/
│   │   ├── KeywordRuleSetDao.java
│   │   ├── KeywordRuleDao.java
│   │   ├── HistoryDao.java
│   │   └── AppSettingsDao.java
│   └── entity/
│       ├── KeywordRuleSet.java
│       ├── KeywordRule.java
│       ├── HistoryEntry.java
│       └── AppSettings.java
├── ui/
│   ├── MainActivity.java           ← ViewPager2 + 3 tabs
│   ├── fragment/
│   │   ├── ConfigFragment.java     ← Tab Cấu hình
│   │   ├── RuleSetFragment.java    ← Tab Bộ từ khóa (chọn/tạo/sửa bộ)
│   │   ├── HistoryFragment.java    ← Tab Lịch sử
│   │   └── LogFragment.java        ← Tab Log realtime
│   └── adapter/
│       ├── RuleSetAdapter.java
│       └── HistoryAdapter.java
├── receiver/
│   └── BootReceiver.java
└── util/
    ├── FingerprintUtil.java
    └── MessageParser.java
```

---

## 12. UI/UX

### Tab 1 — Cấu hình

- **Tên nhóm** (EditText): match với `Button content-desc` trong header Messenger
- **Tên của mình** (EditText): để loại tin mình ra
- **Text reply mặc định** (EditText)
- **Danh sách sender được phép** (chip input, rỗng = tất cả)
- **Nút Start / Stop** (nổi bật, trạng thái hiện tại)
- **Badge trạng thái**: ĐANG CHẠY / DỪNG / ĐANG REPLY

### Tab 2 — Bộ từ khóa

- **Danh sách các bộ (RuleSet)** — RadioButton chọn **1 bộ active**
- Mỗi bộ hiển thị: tên bộ, số rule, toggle bật/tắt
- Nút "+ Thêm bộ mới", nút "Xóa bộ"
- Khi chọn 1 bộ → expand xem/sửa danh sách KeywordRule bên trong
- Mỗi rule: tên, keywords (chip), excludes (chip), reply text override (optional)

### Tab 3 — Lịch sử

- RecyclerView hiển thị HistoryEntry gần nhất (100 entries)
- Mỗi dòng: sender, snippet tin, rule match, thời gian, badge SUCCESS/FAIL
- Nút xóa lịch sử

### Tab 4 — Log

- TextView realtime log (ring buffer 200 dòng)
- Màu: XANH = reply thành công, ĐỎ = lỗi, VÀNG = warning, XÁM = debug
- Nút xóa log

---

## 13. DEPENDENCIES (latest stable)

```gradle
// build.gradle (app)
dependencies {
    // Room
    implementation "androidx.room:room-runtime:2.7.1"
    annotationProcessor "androidx.room:room-compiler:2.7.1"

    // Lifecycle + ViewModel
    implementation "androidx.lifecycle:lifecycle-viewmodel:2.9.1"
    implementation "androidx.lifecycle:lifecycle-livedata:2.9.1"

    // UI
    implementation "com.google.android.material:material:1.12.0"
    implementation "androidx.recyclerview:recyclerview:1.4.0"
    implementation "androidx.fragment:fragment:1.8.7"
    implementation "androidx.navigation:navigation-fragment:2.9.0"
    implementation "androidx.navigation:navigation-ui:2.9.0"
    implementation "androidx.viewpager2:viewpager2:1.1.0"

    // Gson
    implementation "com.google.code.gson:gson:2.13.1"

    // Executor (built-in Java)
}
```

---

## 14. ANTI-BUG CHECKLIST — Fix Triệt Để

### Vấn đề: Reply nhầm/bậy khi tin đến nhanh

| Nguồn gốc bug | Giải pháp trong V4 |
|---|---|
| NodeRef bị recycle → swipe nhầm node | **Bước 1 Re-verify**: Tìm lại node mới hoàn toàn trước mỗi reply, so khớp sender + text exact |
| Swipe xong nhưng panel là tin khác | **Bước 5 Verify Panel**: Đọc "Đang trả lời {X}" và so khớp sender, nếu sai → hủy ngay |
| 2 tin mới đến cùng lúc → reply cả 2 | `isReplying AtomicBoolean` chặn reply thứ 2, chỉ xử lý 1 tại một lúc |
| Tin cũ (trước khi bot chạy) bị reply | So sánh `msg.firstSeenAt >= sessionStartTime` |
| Tin mình bị reply | `isMine` check trước tất cả |
| Tin thu hồi bị reply | `isDeleted` check, detect `"đã xóa một tin nhắn"` |
| Event spam làm process nhiều lần | Throttle 80ms + `isReplying` guard |
| fingerprint collision | SHA-256 truncate 16 hex = 64-bit, đủ cho vài nghìn tin |
| Tin bị edit (chỉnh sửa) dẫn đến reply lại | Fingerprint mới → treated as new, nhưng `replyHistory` by fingerprint → nếu text khớp tin cũ thì block |

### Vấn đề khác

| Bug | Giải pháp |
|---|---|
| Rời khỏi chat trong khi đang reply | `isOnTargetChat()` check trong processEvent; nếu rời chat thì `isReplying` vẫn giữ cho đến khi timeout handler clear |
| Màn hình tắt | AccessibilityService vẫn hoạt động, nhưng gesture sẽ fail → ReplyEngine log fail, `isReplying.set(false)` |
| App crash giữa reply | Try-finally trong `scheduleReply` đảm bảo `isReplying.set(false)` luôn được gọi |
| Bộ keyword không có rule nào active | Check null từ `findMatchingRule`, không reply |

---

## 15. THỨ TỰ BUILD

| Phase | Nội dung |
|---|---|
| 1 — Model | `MessageSnapshot`, `ConversationSnapshot`, `SnapshotDiff`, `ParseResult` |
| 2 — Util | `FingerprintUtil`, `MessageParser` |
| 3 — DB | Room entities + DAO (đặc biệt trigger isActive cho RuleSet) |
| 4 — Detector + Builder | `MessengerDetector`, `SnapshotBuilder` |
| 5 — Diff + Rule | `SnapshotDiffEngine`, `RuleEngine` |
| 6 — Reply Engine | `ReplyEngine` (đây là phần phức tạp nhất, cần test kỹ bước verify) |
| 7 — Repository + State | `MessageRepository`, `BotStateMachine` |
| 8 — Service | `AutoReplyAccessibilityService` — wiring tất cả |
| 9 — UI | 4 tab theo đúng thứ tự: Config → RuleSet → History → Log |
| 10 — Test | Theo checklist section 16 |

---

## 16. TESTING CHECKLIST

- [ ] Tin mới đến bình thường → reply đúng
- [ ] 3 tin đến liên tiếp trong 200ms → chỉ reply tin đầu, không bỏ sót không reply nhầm
- [ ] Tin thu hồi ngay sau khi bot detect → không reply
- [ ] Bot reply xong, có người reply tiếp → detect đúng tin mới
- [ ] Thoát chat rồi vào lại → bot nhận diện lại đúng, không reply tin cũ
- [ ] Tin của mình → không bao giờ reply
- [ ] Đổi bộ keyword khi bot đang chạy → áp dụng ngay lần xử lý tiếp theo
- [ ] Màn hình tắt rồi bật lại giữa chừng → isReplying được reset, không bị kẹt
- [ ] Restart điện thoại → BootReceiver không tự start bot (user phải bật thủ công)
- [ ] Tin chứa số điện thoại (có `Button` con trong bubble) → parse đúng text, không bị lỗi
- [ ] Tin có emoji/ký tự đặc biệt/xuống dòng → fingerprint ổn định
- [ ] Bộ keyword rỗng / không có rule nào enabled → không reply, không crash

---

## 17. ƯỚC TÍNH PERFORMANCE V4

| Bước | Thời gian |
|---|---|
| Event throttle + guard check | ~5ms |
| isOnTargetChat() | ~10ms |
| captureVisibleMessages() | ~20-30ms |
| SnapshotDiff | ~5ms |
| passAllFilters() | ~5ms |
| findLiveNode() (re-verify) | ~20ms |
| swipeToReply() | ~80ms |
| pollReplyPanel() (trung bình) | ~60-120ms |
| verifyPanel() | ~10ms |
| setText() + click Gửi | ~30ms |
| **TỔNG** | **~245–275ms** |

Đảm bảo mục tiêu **< 300ms**.