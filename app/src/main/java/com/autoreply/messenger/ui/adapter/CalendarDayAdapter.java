package com.autoreply.messenger.ui.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.autoreply.messenger.R;

import java.util.Calendar;
import java.util.List;

public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.VH> {

    public interface OnDayClickListener {
        void onDayClick(int day, boolean isCurrentMonth);
    }

    private final List<DayItem> days;
    private final int todayDay;
    private final int todayMonth;
    private final int todayYear;
    private final int displayMonth;
    private final int displayYear;
    private OnDayClickListener listener;

    public static class DayItem {
        public int day;
        public boolean isCurrentMonth;
        public int dayOfWeek; // Calendar.SUNDAY = 1, Calendar.SATURDAY = 7

        public DayItem(int day, boolean isCurrentMonth, int dayOfWeek) {
            this.day = day;
            this.isCurrentMonth = isCurrentMonth;
            this.dayOfWeek = dayOfWeek;
        }
    }

    public CalendarDayAdapter(List<DayItem> days, int displayMonth, int displayYear) {
        this.days = days;
        this.displayMonth = displayMonth;
        this.displayYear = displayYear;
        Calendar now = Calendar.getInstance();
        todayDay = now.get(Calendar.DAY_OF_MONTH);
        todayMonth = now.get(Calendar.MONTH);
        todayYear = now.get(Calendar.YEAR);
    }

    public void setOnDayClickListener(OnDayClickListener l) { listener = l; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DayItem item = days.get(pos);
        h.tvDay.setText(String.valueOf(item.day));

        boolean isToday = item.isCurrentMonth
                && item.day == todayDay
                && displayMonth == todayMonth
                && displayYear == todayYear;

        if (isToday) {
            h.tvDay.setBackgroundResource(R.drawable.bg_calendar_today);
            h.tvDay.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.cal_today_text));
            h.tvDay.setTypeface(null, Typeface.BOLD);
        } else if (!item.isCurrentMonth) {
            h.tvDay.setBackground(null);
            h.tvDay.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.cal_text_dim));
            h.tvDay.setTypeface(null, Typeface.NORMAL);
        } else {
            h.tvDay.setBackground(null);
            // Weekend coloring (Sunday)
            if (item.dayOfWeek == Calendar.SUNDAY) {
                h.tvDay.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.cal_weekend));
            } else {
                h.tvDay.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.cal_text_primary));
            }
            h.tvDay.setTypeface(null, Typeface.NORMAL);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null && item.isCurrentMonth) {
                listener.onDayClick(item.day, true);
            }
        });
    }

    @Override
    public int getItemCount() { return days.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDay;
        VH(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day);
        }
    }
}
