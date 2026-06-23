package com.autoreply.messenger.model;

import java.util.ArrayList;
import java.util.List;

/** Một bộ keyword có tên, danh sách keyword bật và danh sách keyword loại trừ */
public class KeywordSet {
    public String id;
    public String name;
    public List<String> keywords;    // keyword kích hoạt reply
    public List<String> excludes;    // keyword loại trừ — nếu tin chứa bất kỳ cái này → bỏ qua
    public boolean active;           // bộ đang được chọn

    public KeywordSet() {
        id = String.valueOf(System.currentTimeMillis());
        name = "Bộ mới";
        keywords = new ArrayList<>();
        excludes = new ArrayList<>();
        active = false;
    }

    public KeywordSet(String id, String name) {
        this.id = id;
        this.name = name;
        keywords = new ArrayList<>();
        excludes = new ArrayList<>();
        active = false;
    }
}
