package com.autoreply.messenger.model;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public String groupName = "";
    public String replyText = "nhận";
    public List<String> allowedSenders = new ArrayList<>();
    public List<KeywordSet> keywordSets = new ArrayList<>();
    public String activeSetId = "";
    public boolean enabled = false;
    public boolean debugMode = false;
    public int gestureDuration = 60;
    public int duplicateCacheSize = 50;
    // Tên Messenger của người dùng app này — để bot không reply vào tin của chính mình
    // Nếu để trống, bot sẽ tự detect từ bubble bên phải
    public String myName = "";
}