package com.autoreply.messenger.engine;

import java.util.List;

public class KeywordEngine {

    public boolean matches(String text, List<String> keywords) {
        if (text == null || keywords == null || keywords.isEmpty()) return false;
        String lower = text.toLowerCase().trim();
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) continue;
            if (lower.contains(kw.toLowerCase().trim())) return true;
        }
        return false;
    }

    public boolean matchesAny(String text, List<String> excludes) {
        return matches(text, excludes);
    }

    public String findMatched(String text, List<String> keywords) {
        if (text == null || keywords == null) return null;
        String lower = text.toLowerCase().trim();
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) continue;
            if (lower.contains(kw.toLowerCase().trim())) return kw;
        }
        return null;
    }
}
