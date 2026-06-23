package com.autoreply.messenger.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.autoreply.messenger.R;
import com.autoreply.messenger.model.KeywordSet;

import java.util.List;

public class KeywordSetAdapter extends RecyclerView.Adapter<KeywordSetAdapter.VH> {
    public interface Listener {
        void onSelect(int pos);
        void onEdit(int pos);
        void onDelete(int pos);
    }

    private final List<KeywordSet> sets;
    private String activeId;
    private Listener listener;

    public KeywordSetAdapter(List<KeywordSet> sets, String activeId) {
        this.sets = sets;
        this.activeId = activeId;
    }

    public void setListener(Listener l) { listener = l; }
    public void setActiveId(String id) { activeId = id; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_keyword_set, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        KeywordSet ks = sets.get(pos);
        h.radio.setChecked(ks.id.equals(activeId));
        h.tvName.setText(ks.name);
        h.tvCount.setText(ks.keywords.size() + " kw • " + ks.excludes.size() + " loại trừ");
        h.radio.setOnClickListener(v -> { if (listener != null) listener.onSelect(h.getAdapterPosition()); });
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onSelect(h.getAdapterPosition()); });
        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(h.getAdapterPosition()); });
        h.btnDel.setOnClickListener(v -> { if (listener != null) listener.onDelete(h.getAdapterPosition()); });
    }

    @Override public int getItemCount() { return sets.size(); }

    static class VH extends RecyclerView.ViewHolder {
        RadioButton radio; TextView tvName, tvCount; ImageButton btnEdit, btnDel;
        VH(View v) {
            super(v);
            radio   = v.findViewById(R.id.radio_set);
            tvName  = v.findViewById(R.id.tv_set_name);
            tvCount = v.findViewById(R.id.tv_set_count);
            btnEdit = v.findViewById(R.id.btn_edit_set);
            btnDel  = v.findViewById(R.id.btn_del_set);
        }
    }
}
