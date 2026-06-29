package com.autoreply.messenger.service;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.app.NotificationCompat;

import com.autoreply.messenger.activity.MainActivity;
import com.autoreply.messenger.engine.DuplicateEngine;
import com.autoreply.messenger.engine.KeywordEngine;
import com.autoreply.messenger.engine.MessageEngine;
import com.autoreply.messenger.engine.ReplyEngine;
import com.autoreply.messenger.model.Config;
import com.autoreply.messenger.model.KeywordSet;
import com.autoreply.messenger.model.Message;
import com.autoreply.messenger.storage.ConfigManager;
import com.autoreply.messenger.util.Logger;

public class MessengerAccessibilityService extends AccessibilityService {
    public static final String PKG = "com.facebook.orca";
    private static final String CH = "ar_ch";
    private static final int NID = 1001;

    private static MessengerAccessibilityService instance;
    private ConfigManager cfgMgr;
    private MessageEngine msgEngine;
    private KeywordEngine kwEngine;
    private ReplyEngine replyEngine;
    private DuplicateEngine dupEngine;
    private int screenW = 1080;
    private long lastEventTime = 0;
    private static final long THROTTLE_MS = 80;

    public interface StatusListener {
        void onUpdate(String state, String lastOrder, long latency, boolean running);
    }
    private static StatusListener statusListener;
    public static void setStatusListener(StatusListener l) { statusListener = l; }
    public static MessengerAccessibilityService getInstance() { return instance; }
    public static boolean isRunning() { return instance != null; }

    @Override
    public void onServiceConnected() {
        instance = this;
        cfgMgr = ConfigManager.getInstance(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;

        Config cfg = cfgMgr.load();
        Logger.setDebug(cfg.debugMode);
        dupEngine = new DuplicateEngine(cfg.duplicateCacheSize);
        dupEngine.setInitialHash(cfgMgr.loadLastHash());

        msgEngine = new MessageEngine();
        msgEngine.setScreenWidth(screenW);
        msgEngine.setReplyText(cfg.replyText);
        msgEngine.setMyName(cfg.myName);

        kwEngine = new KeywordEngine();
        replyEngine = new ReplyEngine();
        replyEngine.setListener(new ReplyEngine.Listener() {
            @Override public void onState(ReplyEngine.State s) {
                Logger.debug("state=" + s);
                notifyUI(s.name(), null, -1);
            }
            @Override public void onSuccess(String sender, String text, long ms) {
                cfgMgr.saveLastHash(dupEngine.getLastHash());
                notifyUI("IDLE", sender + ": " + text, ms);
            }
            @Override public void onFail(String r) {
                Logger.error("fail: " + r);
                notifyUI("IDLE", null, -1);
            }
        });

        startForeground(NID, buildNotif("🟢 Dịch vụ đang sẵn sàng"));
        Logger.log("service connected screen=" + screenW);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !PKG.equals(pkg.toString())) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;

        // Throttle: bỏ qua event quá gần nhau (RecyclerView spam nhiều event)
        long now = SystemClock.elapsedRealtime();
        if (now - lastEventTime < THROTTLE_MS) return;
        lastEventTime = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        if (!replyEngine.isIdle()) {
            replyEngine.onEvent(root);
            return;
        }

        Config cfg = cfgMgr.getCached();
        if (!cfg.enabled) return;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;

        try { process(root, cfg); }
        catch (Exception e) { Logger.error("process ex: " + e.getMessage()); }
    }

    private void process(AccessibilityNodeInfo root, Config cfg) {
        if (!msgEngine.isChatScreenValid(root, cfg.groupName)) return;

        // Auto-detect myName từ bubble bên phải nếu chưa config
        if (cfg.myName.isEmpty()) {
            String detected = msgEngine.detectMyName(root);
            if (!detected.isEmpty()) {
                cfg.myName = detected;
                cfgMgr.save(cfg);
                msgEngine.setMyName(detected);
            }
        }

        KeywordSet ks = cfgMgr.getActiveSet(cfg);
        if (ks == null || ks.keywords.isEmpty()) {
            Logger.debug("no active keyword set");
            return;
        }

        Message msg = msgEngine.findNewestMessage(root, dupEngine);
        if (msg == null) return;

        if (msg.isMine) {
            dupEngine.markProcessed(msg.hash);
            return;
        }

        if (!cfg.allowedSenders.isEmpty()) {
            boolean allowed = false;
            for (String s : cfg.allowedSenders) {
                if (msg.sender.equalsIgnoreCase(s.trim())) { allowed = true; break; }
            }
            if (!allowed) {
                Logger.debug("sender not allowed: " + msg.sender);
                dupEngine.markProcessed(msg.hash);
                return;
            }
        }

        if (dupEngine.isProcessed(msg.hash)) {
            Logger.debug("skip processed: " + msg.hash);
            return;
        }

        if (!ks.excludes.isEmpty() && kwEngine.matchesAny(msg.text, ks.excludes)) {
            Logger.debug("excluded: " + msg.text);
            dupEngine.markProcessed(msg.hash);
            return;
        }

        if (!kwEngine.matches(msg.text, ks.keywords)) {
            Logger.debug("no match: " + msg.text);
            dupEngine.markProcessed(msg.hash);
            return;
        }

        String matched = kwEngine.findMatched(msg.text, ks.keywords);
        Logger.log("✓ keyword='" + matched + "' text='" + msg.text + "' sender=" + msg.sender);

        dupEngine.markProcessed(msg.hash);
        replyEngine.startReply(this, msg, cfg);
    }

    @Override public void onInterrupt() { Logger.log("interrupted"); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (replyEngine != null) replyEngine.forceReset();
    }

    private void notifyUI(String state, String lastOrder, long latency) {
        if (statusListener != null) statusListener.onUpdate(state, lastOrder, latency, true);
        String txt = "🟢 " + state;
        if (lastOrder != null) txt += " | " + lastOrder;
        if (latency > 0) txt += " " + latency + "ms";
        updateNotif(txt);
    }

    private void updateNotif(String txt) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NID, buildNotif(txt));
    }

    private Notification buildNotif(String txt) {
        NotificationChannel ch = new NotificationChannel(CH, "Quản lý nguồn", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(ch);
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CH)
                .setContentTitle("Dịch vụ tắt nguồn")
                .setContentText(txt)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW).build();
    }

    public void reload() {
        Config cfg = cfgMgr.load();
        Logger.setDebug(cfg.debugMode);
        dupEngine = new DuplicateEngine(cfg.duplicateCacheSize);
        dupEngine.setInitialHash(cfgMgr.loadLastHash());
        msgEngine.setReplyText(cfg.replyText);
        msgEngine.setMyName(cfg.myName);
        if (replyEngine != null) replyEngine.forceReset();
    }
}