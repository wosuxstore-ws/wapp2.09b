package com.wl.orders;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Arrays;
import java.util.List;

// 3-column category grid inside the Categories popup on Home.
// STATIC for now: DEFAULT_CATEGORIES below is hard-coded, and picking one only moves the
// highlight (the product list on Home isn't filtered yet). To act on a pick, use the
// OnCategorySelected callback that HomeActivity passes in.
public class HomeCategoryAdapter extends RecyclerView.Adapter<HomeCategoryAdapter.ViewHolder> {

    public static class Category {
        final String name;
        @DrawableRes final int icon;

        public Category(String name, @DrawableRes int icon) {
            this.name = name;
            this.icon = icon;
        }
    }

    public interface OnCategorySelected {
        void onSelected(int position, Category category);
    }

    public static final List<Category> DEFAULT_CATEGORIES = Arrays.asList(
            new Category("Handsfree",       R.drawable.ic_handsfree),
            new Category("Headphones",      R.drawable.ic_headphones),
            new Category("Data Cable",      R.drawable.ic_cable),
            new Category("Mouse",           R.drawable.ic_mouse),
            new Category("Connector",       R.drawable.ic_connector),
            new Category("Game Controller", R.drawable.ic_gamepad),
            new Category("Charger",         R.drawable.ic_plug),
            new Category("Power Bank",      R.drawable.ic_powerbank),
            new Category("Speaker",         R.drawable.ic_speaker)
    );

    private final List<Category> categories;
    private final OnCategorySelected listener;
    private int selected;

    public HomeCategoryAdapter(List<Category> categories, int selected, OnCategorySelected listener) {
        this.categories = categories;
        this.selected = selected;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Category c = categories.get(position);
        boolean isSelected = position == selected;

        h.icon.setImageResource(c.icon);
        h.label.setText(c.name);
        h.label.setTypeface(isSelected ? h.bold : h.regular);
        h.circle.setBackgroundResource(isSelected ? R.drawable.bg_category_selected : R.drawable.bg_category);
        h.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos == selected) return;
            int previous = selected;
            selected = pos;
            notifyItemChanged(previous);
            notifyItemChanged(pos);
            if (listener != null) listener.onSelected(pos, categories.get(pos));
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View circle;
        final ImageView icon;
        final ImageView check;
        final TextView label;
        final Typeface regular;
        final Typeface bold;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            circle = itemView.findViewById(R.id.categoryCircle);
            icon = itemView.findViewById(R.id.categoryIcon);
            check = itemView.findViewById(R.id.categoryCheck);
            label = itemView.findViewById(R.id.categoryLabel);
            regular = ResourcesCompat.getFont(itemView.getContext(), R.font.poppins_regular);
            bold = ResourcesCompat.getFont(itemView.getContext(), R.font.poppins_bold);
        }
    }
}
