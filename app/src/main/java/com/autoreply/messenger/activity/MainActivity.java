package com.autoreply.messenger.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.autoreply.messenger.R;
import com.autoreply.messenger.databinding.ActivityMainBinding;
import com.autoreply.messenger.model.Config;
import com.autoreply.messenger.model.KeywordSet;
import com.autoreply.messenger.service.MessengerAccessibilityService;
import com.autoreply.messenger.storage.ConfigManager;
import com.autoreply.messenger.ui.adapter.ChipAdapter;
import com.autoreply.messenger.ui.adapter.KeywordSetAdapter;
import com.autoreply.messenger.util.Logger;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private ConfigManager cfgMgr;
    private Config config;
    private KeywordSetAdapter setAdapter;
    private String lastOrder = "—";
    private long lastLatency = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        cfgMgr = ConfigManager.getInstance(this);
        config = cfgMgr.load();
        setupUI();
    }

    private void setupUI() {
        b.etGroupName.setText(config.groupName);
        b.etReplyText.setText(config.replyText);
// Trong setupUI(), thêm sau b.etReplyText.setText(config.replyText);
        b.etMyName.setText(config.myName);

        // Allowed senders chips
        refreshSenderChips();

        // Keyword sets list
        setAdapter = new KeywordSetAdapter(config.keywordSets, config.activeSetId);
        setAdapter.setListener(new KeywordSetAdapter.Listener() {
            @Override public void onSelect(int pos) {
                config.activeSetId = config.keywordSets.get(pos).id;
                setAdapter.setActiveId(config.activeSetId);
                save();
            }
            @Override public void onEdit(int pos) { showEditSetDialog(pos); }
            @Override public void onDelete(int pos) {
                if (config.keywordSets.size() <= 1) {
                    Toast.makeText(MainActivity.this, "Cần ít nhất 1 bộ keyword", Toast.LENGTH_SHORT).show();
                    return;
                }
                config.keywordSets.remove(pos);
                if (!config.keywordSets.isEmpty()) config.activeSetId = config.keywordSets.get(0).id;
                setAdapter.setActiveId(config.activeSetId);
                setAdapter.notifyDataSetChanged();
                save();
            }
        });
        b.rvSets.setLayoutManager(new LinearLayoutManager(this));
        b.rvSets.setAdapter(setAdapter);
        b.rvSets.setNestedScrollingEnabled(false);

        b.btnAddSet.setOnClickListener(v -> showAddSetDialog());

        b.btnAddSender.setOnClickListener(v -> {
            String s = b.etNewSender.getText().toString().trim();
            if (!s.isEmpty()) {
                config.allowedSenders.add(s);
                b.etNewSender.setText("");
                refreshSenderChips();
                save();
            }
        });

        b.btnStart.setOnClickListener(v -> {
            if (!isAccessibilityOn()) { showAccessDialog(); return; }
            config.groupName = b.etGroupName.getText().toString().trim();
            config.replyText = b.etReplyText.getText().toString().trim();
            // Trong btnStart listener, thêm sau config.replyText = ...
            config.myName = b.etMyName.getText().toString().trim();
            config.enabled = true;
            save();
            reload();
            updateStatus();
            Toast.makeText(this, "✅ Bot đã bật", Toast.LENGTH_SHORT).show();
        });

        b.btnStop.setOnClickListener(v -> {
            config.enabled = false;
            save();
            reload();
            updateStatus();
            Toast.makeText(this, "⛔ Bot đã dừng", Toast.LENGTH_SHORT).show();
        });

        b.btnLogs.setOnClickListener(v -> showLogs());
        updateStatus();
    }

    private void refreshSenderChips() {
        ChipAdapter a = new ChipAdapter(config.allowedSenders);
        a.setOnDelete(pos -> {
            config.allowedSenders.remove(pos);
            refreshSenderChips();
            save();
        });
        com.google.android.flexbox.FlexboxLayoutManager layoutManager = new com.google.android.flexbox.FlexboxLayoutManager(this);
        layoutManager.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
        layoutManager.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
        b.rvSenders.setLayoutManager(layoutManager);
        b.rvSenders.setAdapter(a);
        b.rvSenders.setNestedScrollingEnabled(false);
        b.tvSenderHint.setVisibility(config.allowedSenders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddSetDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_edit_set, null);
        EditText etName = v.findViewById(R.id.et_set_name);
        new AlertDialog.Builder(this)
                .setTitle("Tạo bộ keyword mới")
                .setView(v)
                .setPositiveButton("Tạo", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) name = "Bộ " + (config.keywordSets.size() + 1);
                    KeywordSet ks = new KeywordSet();
                    ks.name = name;
                    config.keywordSets.add(ks);
                    if (config.activeSetId.isEmpty()) config.activeSetId = ks.id;
                    setAdapter.setActiveId(config.activeSetId);
                    setAdapter.notifyDataSetChanged();
                    save();
                    // Mở edit dialog ngay
                    showEditSetDialog(config.keywordSets.size() - 1);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showEditSetDialog(int pos) {
        KeywordSet ks = config.keywordSets.get(pos);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_set_detail, null);

        EditText etName    = v.findViewById(R.id.et_set_name);
        EditText etAddKw   = v.findViewById(R.id.et_add_kw);
        EditText etAddEx   = v.findViewById(R.id.et_add_ex);
        androidx.recyclerview.widget.RecyclerView rvKw = v.findViewById(R.id.rv_kw);
        androidx.recyclerview.widget.RecyclerView rvEx = v.findViewById(R.id.rv_ex);

        etName.setText(ks.name);

        ChipAdapter kwAdapter = new ChipAdapter(ks.keywords);
        kwAdapter.setOnDelete(p -> { ks.keywords.remove(p); kwAdapter.notifyDataSetChanged(); });
        com.google.android.flexbox.FlexboxLayoutManager kwLayout = new com.google.android.flexbox.FlexboxLayoutManager(this);
        kwLayout.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
        kwLayout.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
        rvKw.setLayoutManager(kwLayout);
        rvKw.setAdapter(kwAdapter);
        rvKw.setNestedScrollingEnabled(false);

        ChipAdapter exAdapter = new ChipAdapter(ks.excludes);
        exAdapter.setOnDelete(p -> { ks.excludes.remove(p); exAdapter.notifyDataSetChanged(); });
        com.google.android.flexbox.FlexboxLayoutManager exLayout = new com.google.android.flexbox.FlexboxLayoutManager(this);
        exLayout.setFlexDirection(com.google.android.flexbox.FlexDirection.ROW);
        exLayout.setFlexWrap(com.google.android.flexbox.FlexWrap.WRAP);
        rvEx.setLayoutManager(exLayout);
        rvEx.setAdapter(exAdapter);
        rvEx.setNestedScrollingEnabled(false);

        v.findViewById(R.id.btn_add_kw).setOnClickListener(x -> {
            String kw = etAddKw.getText().toString().trim();
            if (!kw.isEmpty()) { ks.keywords.add(kw); etAddKw.setText(""); kwAdapter.notifyDataSetChanged(); }
        });
        v.findViewById(R.id.btn_add_ex).setOnClickListener(x -> {
            String ex = etAddEx.getText().toString().trim();
            if (!ex.isEmpty()) { ks.excludes.add(ex); etAddEx.setText(""); exAdapter.notifyDataSetChanged(); }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chỉnh bộ keyword")
                .setView(v)
                .setPositiveButton("Lưu", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) ks.name = name;
                    setAdapter.notifyDataSetChanged();
                    save();
                })
                .setNegativeButton("Hủy", null)
                .create();
        dialog.show();

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int maxH = (int) (dm.heightPixels * 0.75);
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            v.post(() -> {
                if (v.getHeight() > maxH) {
                    window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, maxH);
                }
            });
        }
    }

    private void showLogs() {
        StringBuilder sb = new StringBuilder();
        for (String l : Logger.getLogs()) sb.append(l).append("\n");
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        float scale = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * scale + 0.5f);
        tv.setPadding(pad, pad, pad, pad);
        tv.setText(sb.length() > 0 ? sb.toString() : "(trống)");
        tv.setTextSize(12);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextColor(0xFF37474F);
        sv.addView(tv);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Logs")
                .setView(sv)
                .setPositiveButton("Đóng", null)
                .setNeutralButton("Xóa", (d, w) -> {
                    Logger.clear();
                    Toast.makeText(this, "Đã xóa logs", Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.show();

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int maxH = (int) (dm.heightPixels * 0.75);
            sv.post(() -> {
                if (sv.getHeight() > maxH) {
                    window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, maxH);
                }
            });
        }
    }


    @Override protected void onResume() {
        super.onResume();
        updateStatus();
        MessengerAccessibilityService.setStatusListener((state, lastOrd, latency, running) ->
            runOnUiThread(() -> {
                if (lastOrd != null) lastOrder = lastOrd;
                if (latency > 0) lastLatency = latency;
                b.tvStatus.setText(running && config.enabled ? "🟢 Running" : "🔴 Stopped");
                b.tvState.setText(state);
                b.tvLastOrder.setText(lastOrder);
                if (lastLatency > 0) b.tvLatency.setText(lastLatency + " ms");
            }));
        handler.postDelayed(refresher, 3000);
    }

    @Override protected void onPause() {
        super.onPause();
        MessengerAccessibilityService.setStatusListener(null);
        handler.removeCallbacks(refresher);
    }

    private final Runnable refresher = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 3000); }
    };

    private void updateStatus() {
        boolean svcOk = MessengerAccessibilityService.isRunning();
        boolean botOn = config.enabled;
        if (!svcOk) b.tvStatus.setText("⚠️ Chưa bật Accessibility");
        else if (botOn) b.tvStatus.setText("🟢 Running");
        else b.tvStatus.setText("🔴 Stopped");
        b.btnStart.setEnabled(!botOn);
        b.btnStop.setEnabled(botOn);
    }

    private void save() { cfgMgr.save(config); }
    private void reload() {
        if (MessengerAccessibilityService.getInstance() != null)
            MessengerAccessibilityService.getInstance().reload();
    }

    private boolean isAccessibilityOn() {
        try {
            int en = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (en != 1) return false;
            String svcs = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return svcs != null && svcs.toLowerCase().contains(getPackageName().toLowerCase());
        } catch (Exception e) { return false; }
    }

    private void showAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cần bật Accessibility")
                .setMessage("Vào Settings → Accessibility → " + getString(R.string.accessibility_label) + " → BẬT")
                .setPositiveButton("Mở Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Hủy", null).show();
    }
}
