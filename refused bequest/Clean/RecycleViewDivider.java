package com.example.divider;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

/**
 * Divider for RecyclerView items, supporting vertical or horizontal orientation
 * and optional footer divider.
 */
public class RecycleViewDivider extends RecyclerView.ItemDecoration {
    public enum Orientation { HORIZONTAL, VERTICAL }

    private final Drawable divider;
    private final Orientation orientation;
    private final boolean showFooterDivider;
    private final Rect bounds = new Rect();

    private RecycleViewDivider(@NonNull Drawable divider,
                                @NonNull Orientation orientation,
                                boolean showFooterDivider) {
        this.divider = Objects.requireNonNull(divider, "divider must not be null");
        this.orientation = Objects.requireNonNull(orientation, "orientation must not be null");
        this.showFooterDivider = showFooterDivider;
    }

    /**
     * Create a divider from drawable resource.
     */
    public static RecycleViewDivider create(@NonNull Context context,
                                            @NonNull Orientation orientation,
                                            @DrawableRes int resId,
                                            boolean showFooterDivider) {
        Drawable dr = ContextCompat.getDrawable(context, resId);
        return new RecycleViewDivider(dr, orientation, showFooterDivider);
    }

    /**
     * Create a divider from existing Drawable.
     */
    public static RecycleViewDivider create(@NonNull Drawable divider,
                                            @NonNull Orientation orientation) {
        return new RecycleViewDivider(divider, orientation, false);
    }

    @Override
    public void onDraw(@NonNull Canvas canvas,
                       @NonNull RecyclerView parent,
                       @NonNull RecyclerView.State state) {
        if (!(parent.getLayoutManager() instanceof LinearLayoutManager)) {
            return;
        }
        if (orientation == Orientation.VERTICAL) {
            drawVertical(canvas, parent);
        } else {
            drawHorizontal(canvas, parent);
        }
    }

    private void drawVertical(@NonNull Canvas canvas,
                              @NonNull RecyclerView parent) {
        canvas.save();
        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();
        if (parent.getClipToPadding()) {
            canvas.clipRect(left, parent.getPaddingTop(), right,
                            parent.getHeight() - parent.getPaddingBottom());
        }
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (i == childCount - 1 && !showFooterDivider) continue;
            View child = parent.getChildAt(i);
            parent.getDecoratedBoundsWithMargins(child, bounds);
            int bottom = bounds.bottom + Math.round(ViewCompat.getTranslationY(child));
            int top = bottom - divider.getIntrinsicHeight();
            divider.setBounds(left, top, right, bottom);
            divider.draw(canvas);
        }
        canvas.restore();
    }

    private void drawHorizontal(@NonNull Canvas canvas,
                                @NonNull RecyclerView parent) {
        canvas.save();
        int top = parent.getPaddingTop();
        int bottom = parent.getHeight() - parent.getPaddingBottom();
        if (parent.getClipToPadding()) {
            canvas.clipRect(parent.getPaddingLeft(), top,
                            parent.getWidth() - parent.getPaddingRight(), bottom);
        }
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (i == childCount - 1 && !showFooterDivider) continue;
            View child = parent.getChildAt(i);
            parent.getDecoratedBoundsWithMargins(child, bounds);
            int right = bounds.right + Math.round(ViewCompat.getTranslationX(child));
            int left = right - divider.getIntrinsicWidth();
            divider.setBounds(left, top, right, bottom);
            divider.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect,
                               @NonNull View view,
                               @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        if (orientation == Orientation.VERTICAL) {
            outRect.set(0, 0, 0, divider.getIntrinsicHeight());
        } else {
            outRect.set(0, 0, divider.getIntrinsicWidth(), 0);
        }
    }
}
