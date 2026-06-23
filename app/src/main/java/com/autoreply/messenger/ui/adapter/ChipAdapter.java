package com.autoreply.messenger.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autoreply.messenger.R;

import java.util.List;

public class ChipAdapter extends RecyclerView.Adapter<ChipAdapter.VH> {
    public interface OnDelete { void onDelete(int pos); }
    private final List<String> items;
    private OnDelete listener;

    public ChipAdapter(List<String> items) { this.items = items; }
    public void setOnDelete(OnDelete l) { listener = l; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_chip, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.tv.setText(items.get(pos));
        h.btn.setOnClickListener(v -> { if (listener != null) listener.onDelete(h.getAdapterPosition()); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tv; ImageButton btn;
        VH(View v) { super(v); tv = v.findViewById(R.id.tv_chip); btn = v.findViewById(R.id.btn_chip_del); }
    }
}
