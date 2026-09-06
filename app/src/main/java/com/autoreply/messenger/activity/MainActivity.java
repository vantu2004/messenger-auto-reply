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
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.flexbox.FlexboxLayout;
import androidx.core.widget.NestedScrollView;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private ConfigManager cfgMgr;
    private Config config;
    private KeywordSetAdapter setAdapter;
    private String lastOrder = "—";
    private long lastLatency = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean suppressSwitchListener = false;

    private final ActivityResultLauncher<Intent> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                writeConfigToUri(uri);
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                readConfigFromUri(uri);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        cfgMgr = ConfigManager.getInstance(this);
        config = cfgMgr.load();
        setupUI();
    }

    @Override
    public void onBackPressed() {
        // Save fields before going back to Calendar
        saveFields();
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void setupUI() {
        b.etGroupName.setText(config.groupName);
        b.etReplyText.setText(config.replyText);
        b.etMyName.setText(config.myName);

        // ==================== Master Toggle ====================
        suppressSwitchListener = true;
        b.switchMaster.setChecked(config.enabled);
        suppressSwitchListener = false;
        updateHeroUI(config.enabled);

        b.switchMaster.setOnCheckedChangeListener((v, checked) -> {
            if (suppressSwitchListener) return;
            if (checked && !isAccessibilityOn()) {
                suppressSwitchListener = true;
                b.switchMaster.setChecked(false);
                suppressSwitchListener = false;
                showAccessDialog();
                return;
            }
            // Save fields before toggling
            saveFields();
            config.enabled = checked;
            save();
            reload();
            updateHeroUI(checked);
            Toast.makeText(this, checked ? "✅ Bot đã bật" : "⛔ Bot đã dừng", Toast.LENGTH_SHORT).show();
        });

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

        b.btnLogs.setOnClickListener(v -> showLogs());
        b.btnBackupRestore.setOnClickListener(v -> showBackupRestoreDialog());
        updateStatus();
    }

    /**
     * Update hero section visuals based on bot state.
     */
    private void updateHeroUI(boolean enabled) {
        if (enabled) {
            b.tvHeroStatus.setText("Bot đang chạy");
            b.tvHeroStatus.setTextColor(ContextCompat.getColor(this, R.color.glass_green));
            b.viewGlow.setBackgroundResource(R.drawable.bg_hero_glow_on);
        } else {
            b.tvHeroStatus.setText("Bot đã tắt");
            b.tvHeroStatus.setTextColor(ContextCompat.getColor(this, R.color.glass_red));
            b.viewGlow.setBackgroundResource(R.drawable.bg_hero_glow_off);
        }
    }

    /**
     * Save text fields to config without toggling enabled state.
     */
    private void saveFields() {
        config.groupName = b.etGroupName.getText().toString().trim();
        config.replyText = b.etReplyText.getText().toString().trim();
        config.myName = b.etMyName.getText().toString().trim();
        save();
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
        com.google.android.flexbox.FlexboxLayout flexKw = v.findViewById(R.id.flex_kw);
        com.google.android.flexbox.FlexboxLayout flexEx = v.findViewById(R.id.flex_ex);
        androidx.core.widget.NestedScrollView scrollRoot = v.findViewById(R.id.scroll_root);

        etName.setText(ks.name);

        // ★ FIX: Dùng FlexboxLayout (ViewGroup) thay RecyclerView
        // RecyclerView trong NestedScrollView bị bug measure → cắt items khi > ~50
        populateChips(flexKw, ks.keywords, scrollRoot);
        populateChips(flexEx, ks.excludes, scrollRoot);

        v.findViewById(R.id.btn_add_kw).setOnClickListener(x -> {
            String kw = etAddKw.getText().toString().trim();
            if (!kw.isEmpty()) {
                ks.keywords.add(kw);
                etAddKw.setText("");
                populateChips(flexKw, ks.keywords, scrollRoot);
                scrollRoot.post(() -> scrollRoot.fullScroll(View.FOCUS_DOWN));
            }
        });
        v.findViewById(R.id.btn_add_ex).setOnClickListener(x -> {
            String ex = etAddEx.getText().toString().trim();
            if (!ex.isEmpty()) {
                ks.excludes.add(ex);
                etAddEx.setText("");
                populateChips(flexEx, ks.excludes, scrollRoot);
                scrollRoot.post(() -> scrollRoot.fullScroll(View.FOCUS_DOWN));
            }
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

        // Set dialog height cố định 80% screen
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int dialogH = (int) (dm.heightPixels * 0.80);
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, dialogH);
        }
    }

    /**
     * Populate FlexboxLayout với chip views từ list.
     * Mỗi chip inflate từ item_chip.xml, click X → xóa item → rebuild.
     */
    private void populateChips(com.google.android.flexbox.FlexboxLayout container,
                               java.util.List<String> items,
                               androidx.core.widget.NestedScrollView scrollRoot) {
        container.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            View chip = LayoutInflater.from(this).inflate(R.layout.item_chip, container, false);
            ((TextView) chip.findViewById(R.id.tv_chip)).setText(items.get(i));
            final int idx = i;
            chip.findViewById(R.id.btn_chip_del).setOnClickListener(x -> {
                items.remove(idx);
                populateChips(container, items, scrollRoot);
            });
            container.addView(chip);
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
        tv.setTextColor(getColor(R.color.white));
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
        // Refresh switch state (in case service changed)
        suppressSwitchListener = true;
        b.switchMaster.setChecked(config.enabled);
        suppressSwitchListener = false;
        updateHeroUI(config.enabled);
        updateStatus();
        MessengerAccessibilityService.setStatusListener((state, lastOrd, latency, running) ->
            runOnUiThread(() -> {
                if (lastOrd != null) lastOrder = lastOrd;
                if (latency > 0) lastLatency = latency;
                b.tvStatus.setText(state);
                b.tvLastOrder.setText(lastOrder);
                if (lastLatency > 0) b.tvLatency.setText(lastLatency + " ms");
                // Update hero based on actual running state
                boolean isOn = running && config.enabled;
                updateHeroUI(isOn);
                suppressSwitchListener = true;
                b.switchMaster.setChecked(isOn);
                suppressSwitchListener = false;
            }));
        handler.postDelayed(refresher, 3000);
    }

    @Override protected void onPause() {
        super.onPause();
        MessengerAccessibilityService.setStatusListener(null);
        handler.removeCallbacks(refresher);
        // Auto-save fields when leaving
        saveFields();
    }

    private final Runnable refresher = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 3000); }
    };

    private void updateStatus() {
        boolean svcOk = MessengerAccessibilityService.isRunning();
        boolean botOn = config.enabled;
        if (!svcOk) {
            b.tvStatus.setText("⚠️ Chưa bật Accessibility");
        } else {
            b.tvStatus.setText(botOn ? "RUNNING" : "IDLE");
        }
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

    private void showBackupRestoreDialog() {
        String[] options = {
                "Xuất cấu hình (Sao chép vào Clipboard)",
                "Xuất cấu hình (Lưu thành tệp .json)",
                "Nhập cấu hình (Dán từ Clipboard)",
                "Nhập cấu hình (Chọn tệp .json)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Sao lưu & Khôi phục")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            exportToClipboard();
                            break;
                        case 1:
                            exportToFile();
                            break;
                        case 2:
                            importFromClipboard();
                            break;
                        case 3:
                            importFromFile();
                            break;
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void exportToClipboard() {
        try {
            String json = cfgMgr.toJsonString(config);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("MessengerAutoReply Config", json);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Đã sao chép cấu hình vào Clipboard", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi sao chép: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Toast.makeText(this, "Clipboard trống", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                Toast.makeText(this, "Clipboard trống", Toast.LENGTH_SHORT).show();
                return;
            }
            String pasteData = clip.getItemAt(0).getText().toString().trim();
            if (TextUtils.isEmpty(pasteData)) {
                Toast.makeText(this, "Nội dung Clipboard trống", Toast.LENGTH_SHORT).show();
                return;
            }
            Config imported = cfgMgr.fromJsonString(pasteData);
            if (imported.keywordSets == null || imported.keywordSets.isEmpty()) {
                Toast.makeText(this, "Mã cấu hình không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            applyAndSaveImportedConfig(imported);
            Toast.makeText(this, "Khôi phục cấu hình thành công", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khôi phục: " + e.getMessage() + "\nHãy đảm bảo bạn đã sao chép đúng mã cấu hình.", Toast.LENGTH_LONG).show();
        }
    }

    private void exportToFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "messenger_auto_reply_config.json");
        createFileLauncher.launch(intent);
    }

    private void importFromFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        openFileLauncher.launch(intent);
    }

    private void writeConfigToUri(Uri uri) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
             FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor())) {
            String json = cfgMgr.toJsonString(config);
            fileOutputStream.write(json.getBytes());
            Toast.makeText(this, "Đã lưu cấu hình vào tệp thành công", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi lưu tệp: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void readConfigFromUri(Uri uri) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
             FileInputStream fileInputStream = new FileInputStream(pfd.getFileDescriptor())) {
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fileInputStream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, read));
            }
            Config imported = cfgMgr.fromJsonString(sb.toString());
            applyAndSaveImportedConfig(imported);
            Toast.makeText(this, "Đã khôi phục cấu hình từ tệp", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi đọc tệp: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyAndSaveImportedConfig(Config imported) {
        config = imported;
        save();
        reload();
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }
}
