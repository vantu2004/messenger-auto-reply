package com.autoreply.messenger.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.autoreply.messenger.model.Config;
import com.autoreply.messenger.model.KeywordSet;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class ConfigManager {
    private static final String PREF = "ar_config_v4";
    private static ConfigManager instance;
    private final SharedPreferences prefs;
    private Config cached;

    private ConfigManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized ConfigManager getInstance(Context ctx) {
        if (instance == null) instance = new ConfigManager(ctx);
        return instance;
    }

    public Config load() {
        Config c = new Config();
        c.groupName = prefs.getString("group_name", "");
        c.replyText = prefs.getString("reply_text", "nhận");
        c.enabled = prefs.getBoolean("enabled", false);
        c.debugMode = prefs.getBoolean("debug", false);
        c.gestureDuration = prefs.getInt("gesture_dur", 60);
        c.duplicateCacheSize = prefs.getInt("cache_size", 50);
        c.activeSetId = prefs.getString("active_set_id", "");
        c.myName = prefs.getString("my_name", "");

        c.allowedSenders = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("allowed_senders", "[]"));
            for (int i = 0; i < arr.length(); i++) c.allowedSenders.add(arr.getString(i));
        } catch (Exception ignored) {}

        c.keywordSets = new ArrayList<>();
        try {
            JSONArray sets = new JSONArray(prefs.getString("keyword_sets", "[]"));
            for (int i = 0; i < sets.length(); i++) {
                JSONObject obj = sets.getJSONObject(i);
                KeywordSet ks = new KeywordSet(obj.getString("id"), obj.getString("name"));
                ks.active = obj.optBoolean("active", false);
                JSONArray kw = obj.optJSONArray("keywords");
                if (kw != null) for (int j = 0; j < kw.length(); j++) ks.keywords.add(kw.getString(j));
                JSONArray ex = obj.optJSONArray("excludes");
                if (ex != null) for (int j = 0; j < ex.length(); j++) ks.excludes.add(ex.getString(j));
                c.keywordSets.add(ks);
            }
        } catch (Exception ignored) {}

        cached = c;
        return c;
    }

    public void save(Config c) {
        cached = c;
        SharedPreferences.Editor e = prefs.edit();
        e.putString("group_name", c.groupName);
        e.putString("reply_text", c.replyText);
        e.putBoolean("enabled", c.enabled);
        e.putBoolean("debug", c.debugMode);
        e.putInt("gesture_dur", c.gestureDuration);
        e.putInt("cache_size", c.duplicateCacheSize);
        e.putString("active_set_id", c.activeSetId);
        e.putString("my_name", c.myName);

        try {
            JSONArray senders = new JSONArray();
            for (String s : c.allowedSenders) senders.put(s);
            e.putString("allowed_senders", senders.toString());

            JSONArray sets = new JSONArray();
            for (KeywordSet ks : c.keywordSets) {
                JSONObject obj = new JSONObject();
                obj.put("id", ks.id);
                obj.put("name", ks.name);
                obj.put("active", ks.active);
                JSONArray kw = new JSONArray();
                for (String k : ks.keywords) kw.put(k);
                obj.put("keywords", kw);
                JSONArray ex = new JSONArray();
                for (String x : ks.excludes) ex.put(x);
                obj.put("excludes", ex);
                sets.put(obj);
            }
            e.putString("keyword_sets", sets.toString());
        } catch (Exception ignored) {}
        e.apply();
    }

    public void saveLastHash(String hash) { prefs.edit().putString("last_hash", hash).apply(); }
    public String loadLastHash() { return prefs.getString("last_hash", ""); }
    public Config getCached() { return cached != null ? cached : load(); }

    public KeywordSet getActiveSet(Config c) {
        if (c.keywordSets.isEmpty()) return null;
        for (KeywordSet ks : c.keywordSets) {
            if (ks.id.equals(c.activeSetId)) return ks;
        }
        return null;
    }

    public String toJsonString(Config c) {
        try {
            JSONObject root = new JSONObject();
            root.put("groupName", c.groupName);
            root.put("replyText", c.replyText);
            root.put("enabled", c.enabled);
            root.put("debugMode", c.debugMode);
            root.put("gestureDuration", c.gestureDuration);
            root.put("duplicateCacheSize", c.duplicateCacheSize);
            root.put("activeSetId", c.activeSetId);
            root.put("myName", c.myName);

            JSONArray senders = new JSONArray();
            if (c.allowedSenders != null) {
                for (String s : c.allowedSenders) {
                    senders.put(s);
                }
            }
            root.put("allowedSenders", senders);

            JSONArray sets = new JSONArray();
            if (c.keywordSets != null) {
                for (KeywordSet ks : c.keywordSets) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", ks.id);
                    obj.put("name", ks.name);
                    obj.put("active", ks.active);
                    JSONArray kw = new JSONArray();
                    for (String k : ks.keywords) kw.put(k);
                    obj.put("keywords", kw);
                    JSONArray ex = new JSONArray();
                    for (String x : ks.excludes) ex.put(x);
                    obj.put("excludes", ex);
                    sets.put(obj);
                }
            }
            root.put("keywordSets", sets);
            return root.toString(4);
        } catch (Exception e) {
            return "";
        }
    }

    public Config fromJsonString(String jsonStr) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        Config c = new Config();
        c.groupName = root.optString("groupName", "");
        c.replyText = root.optString("replyText", "nhận");
        c.enabled = root.optBoolean("enabled", false);
        c.debugMode = root.optBoolean("debugMode", false);
        c.gestureDuration = root.optInt("gestureDuration", 150);
        c.duplicateCacheSize = root.optInt("duplicateCacheSize", 50);
        c.activeSetId = root.optString("activeSetId", "");
        c.myName = root.optString("myName", "");

        c.allowedSenders = new ArrayList<>();
        JSONArray senders = root.optJSONArray("allowedSenders");
        if (senders != null) {
            for (int i = 0; i < senders.length(); i++) {
                c.allowedSenders.add(senders.getString(i));
            }
        }

        c.keywordSets = new ArrayList<>();
        JSONArray sets = root.optJSONArray("keywordSets");
        if (sets != null) {
            for (int i = 0; i < sets.length(); i++) {
                JSONObject obj = sets.getJSONObject(i);
                KeywordSet ks = new KeywordSet(obj.getString("id"), obj.getString("name"));
                ks.active = obj.optBoolean("active", false);
                JSONArray kw = obj.optJSONArray("keywords");
                if (kw != null) {
                    for (int j = 0; j < kw.length(); j++) ks.keywords.add(kw.getString(j));
                }
                JSONArray ex = obj.optJSONArray("excludes");
                if (ex != null) {
                    for (int j = 0; j < ex.length(); j++) ks.excludes.add(ex.getString(j));
                }
                c.keywordSets.add(ks);
            }
        }
        return c;
    }
}