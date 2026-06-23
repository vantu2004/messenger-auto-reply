package com.autoreply.messenger.model;

import android.graphics.Rect;

/** Snapshot bất biến — lưu Rect tại thời điểm detect, không giữ node */
public class Message {
    public final String sender;
    public final String text;
    public final String hash;
    public final boolean isMine;
    public final Rect bubbleBounds;

    public Message(String sender, String text, String hash, boolean isMine, Rect bubbleBounds) {
        this.sender = sender;
        this.text = text;
        this.hash = hash;
        this.isMine = isMine;
        this.bubbleBounds = new Rect(bubbleBounds);
    }

    @Override public String toString() { return sender + ": " + text; }
}
