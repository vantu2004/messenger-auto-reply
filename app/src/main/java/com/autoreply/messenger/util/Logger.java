package com.autoreply.messenger.util;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.*;

public class Logger {
    private static final String TAG = "AutoReply";
    private static final int MAX = 300;
    private static final List<String> buf = new ArrayList<>();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private static boolean debug = false;
    private static OnLogListener listener;

    public interface OnLogListener { void onLog(String line); }

    public static void setDebug(boolean d) { debug = d; }
    public static void setListener(OnLogListener l) { listener = l; }

    public static void log(String msg) { append(msg, false); }
    public static void debug(String msg) { if (debug) append("[D] " + msg, false); }
    public static void error(String msg) { append("[ERROR] " + msg, true); }

    private static void append(String msg, boolean isErr) {
        String line = sdf.format(new Date()) + " " + msg;
        if (isErr) Log.e(TAG, line); else Log.d(TAG, line);
        synchronized (buf) {
            buf.add(line);
            if (buf.size() > MAX) buf.remove(0);
        }
        if (listener != null) listener.onLog(line);
    }

    public static List<String> getLogs() { synchronized (buf) { return new ArrayList<>(buf); } }
    public static void clear() { synchronized (buf) { buf.clear(); } }
}
