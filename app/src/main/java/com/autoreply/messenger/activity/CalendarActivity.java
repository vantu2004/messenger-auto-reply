package com.autoreply.messenger.activity;

import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autoreply.messenger.R;
import com.autoreply.messenger.ui.adapter.CalendarDayAdapter;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Fake Calendar front-end — trông giống app lịch bình thường.
 * Long-press vùng ẩn ở footer → xác thực thiết bị → mở Bot control.
 */
public class CalendarActivity extends AppCompatActivity {

    private TextView tvMonthYear;
    private RecyclerView rvCalendar;
    private LinearLayout llWeekdayHeaders;
    private Calendar displayCalendar;

    private static final String[] WEEKDAYS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        tvMonthYear = findViewById(R.id.tv_month_year);
        rvCalendar = findViewById(R.id.rv_calendar);
        llWeekdayHeaders = findViewById(R.id.ll_weekday_headers);

        displayCalendar = Calendar.getInstance();

        setupWeekdayHeaders();
        setupCalendarGrid();
        updateCalendar();

        // Month navigation
        findViewById(R.id.btn_prev_month).setOnClickListener(v -> {
            displayCalendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });
        findViewById(R.id.btn_next_month).setOnClickListener(v -> {
            displayCalendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });

        // Today button — jump back to current month
        findViewById(R.id.btn_today).setOnClickListener(v -> {
            displayCalendar = Calendar.getInstance();
            updateCalendar();
        });

        // ★ Hidden zone: long-press footer version text → authenticate → open bot
        TextView tvFooter = findViewById(R.id.tv_footer_version);
        tvFooter.setOnLongClickListener(v -> {
            attemptAuthentication();
            return true;
        });
    }

    private void setupWeekdayHeaders() {
        llWeekdayHeaders.removeAllViews();
        for (int i = 0; i < WEEKDAYS.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(WEEKDAYS[i]);
            tv.setTextSize(12);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            // Sunday (last column) = red
            if (i == 6) {
                tv.setTextColor(ContextCompat.getColor(this, R.color.cal_weekend));
            } else {
                tv.setTextColor(ContextCompat.getColor(this, R.color.cal_text_secondary));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            tv.setLayoutParams(lp);
            llWeekdayHeaders.addView(tv);
        }
    }

    private void setupCalendarGrid() {
        GridLayoutManager glm = new GridLayoutManager(this, 7);
        rvCalendar.setLayoutManager(glm);
        rvCalendar.setHasFixedSize(true);
    }

    private void updateCalendar() {
        // Update month/year header
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", new Locale("vi"));
        tvMonthYear.setText(capitalizeFirst(sdf.format(displayCalendar.getTime())));

        // Build day items
        List<CalendarDayAdapter.DayItem> days = buildDays();
        CalendarDayAdapter adapter = new CalendarDayAdapter(days,
                displayCalendar.get(Calendar.MONTH),
                displayCalendar.get(Calendar.YEAR));
        adapter.setOnDayClickListener((day, isCurrentMonth) -> {
            Snackbar.make(rvCalendar, "Không có sự kiện", Snackbar.LENGTH_SHORT).show();
        });
        rvCalendar.setAdapter(adapter);
    }

    private List<CalendarDayAdapter.DayItem> buildDays() {
        List<CalendarDayAdapter.DayItem> items = new ArrayList<>();

        Calendar cal = (Calendar) displayCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);

        // dayOfWeek: Calendar.SUNDAY=1 ... SATURDAY=7
        // We want Monday=first column → offset
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        // Convert to Monday-based: Mon=0, Tue=1 ... Sun=6
        int offset = (firstDayOfWeek + 5) % 7;

        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Previous month's trailing days
        Calendar prev = (Calendar) cal.clone();
        prev.add(Calendar.MONTH, -1);
        int prevDays = prev.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = offset - 1; i >= 0; i--) {
            int day = prevDays - i;
            items.add(new CalendarDayAdapter.DayItem(day, false, 0));
        }

        // Current month days
        for (int d = 1; d <= daysInMonth; d++) {
            cal.set(Calendar.DAY_OF_MONTH, d);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            items.add(new CalendarDayAdapter.DayItem(d, true, dow));
        }

        // Next month's leading days (fill up to 42 cells = 6 rows)
        int remaining = 42 - items.size();
        for (int d = 1; d <= remaining; d++) {
            items.add(new CalendarDayAdapter.DayItem(d, false, 0));
        }

        return items;
    }

    // ==================== Authentication ====================

    private void attemptAuthentication() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isDeviceSecure()) {
            // Device has lock screen → authenticate
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Xác thực")
                    .setSubtitle("Xác thực để tiếp tục")
                    .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            BiometricPrompt prompt = new BiometricPrompt(this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            openMainActivity();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            // Silently fail — no suspicious feedback
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            // Silently fail
                        }
                    });
            prompt.authenticate(info);
        } else {
            // No lock screen → open directly
            openMainActivity();
        }
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
