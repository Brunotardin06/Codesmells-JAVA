package com.blankj.debug;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

/**
 * A floating debug icon that can be dragged, snapped to screen edges,
 * and opens the debug menu on click.
 */
public class DebugIcon extends RelativeLayout {
    private static DebugIcon instance;
    private final Rect tmpRect = new Rect();
    private int iconResId;

    private DebugIcon(@NonNull Context context) {
        super(context);
        init();
    }

    private DebugIcon(@NonNull Context context, @DrawableRes int resId) {
        super(context);
        this.iconResId = resId;
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.du_debug_icon, this);
        ShadowHelper.applyDebugIcon(this);

        // Touch handling
        TouchHandler touchHandler = new TouchHandler();
        setOnTouchListener(touchHandler);

        // Click opens debug menu
        setOnClickListener(v -> {
            if (PermissionUtils.canDrawOverlays(getContext())) {
                DebugMenu.getInstance().show();
            } else {
                PermissionUtils.requestDrawOverlays(getContext(), granted -> {
                    if (granted) DebugMenu.getInstance().show();
                    else Toast.makeText(getContext(), R.string.de_permission_tips, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Obtain (or create) the singleton DebugIcon.
     */
    public static DebugIcon getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new DebugIcon(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Set icon resource ID and update view.
     */
    public void setIcon(@DrawableRes int resId) {
        if (resId == iconResId) return;
        iconResId = resId;
        ImageView iv = findViewById(R.id.debugIconIv);
        if (iv != null) {
            iv.setImageResource(resId);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restorePosition();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        savePosition();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        snapToEdge();
    }

    private void restorePosition() {
        int x = DebugConfig.getViewX();
        int y = DebugConfig.getViewY(getContext());
        setX(x);
        setY(y);
        snapToEdge();
    }

    private void savePosition() {
        DebugConfig.saveViewX((int) getX());
        DebugConfig.saveViewY((int) getY());
    }

    private void snapToEdge() {
        post(() -> {
            View content = getRootView().findViewById(android.R.id.content);
            if (content == null) return;
            int parentW = content.getWidth();
            int halfIcon = getWidth() / 2;
            float newX = (getX() + halfIcon > parentW / 2f)
                ? parentW - getWidth()
                : 0;
            setX(newX);
        });
    }

    /**
     * Inner touch listener for drag & snap behavior.
     */
    private class TouchHandler implements OnTouchListener {
        private float downX, downY;
        private int parentW, parentH, statusBarH;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX() - v.getX();
                    downY = e.getRawY() - v.getY();
                    View content = getRootView().findViewById(android.R.id.content);
                    parentW = content.getWidth();
                    parentH = content.getHeight();
                    statusBarH = BarUtils.getStatusBarHeight();
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float newX = e.getRawX() - downX;
                    float newY = Math.max(statusBarH, e.getRawY() - downY);
                    v.setX(clamp(newX, 0, parentW - v.getWidth()));
                    v.setY(clamp(newY, statusBarH, parentH - v.getHeight()));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    snapToEdge();
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    return true;
                default:
                    return false;
            }
        }

        private float clamp(float val, float min, float max) {
            return Math.max(min, Math.min(max, val));
        }
    }
}
