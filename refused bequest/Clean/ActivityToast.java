package com.blankj.toast;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.blankj.utils.UtilsBridge;
import com.blankj.toast.base.AbsToast;
import com.blankj.toast.base.IToast;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Toast implementation that displays in each foreground Activity's window.
 */
static final class ActivityToast extends AbsToast {
    private final AtomicInteger showCount = new AtomicInteger();
    private UtilsBridge.ActivityLifecycleCallbacks lifecycleCallback;
    private IToast systemToast;

    ActivityToast(ToastUtils toastUtils) {
        super(toastUtils);
    }

    @Override
    public void show(int duration) {
        if (mToast == null) return;

        if (!UtilsBridge.isAppForeground()) {
            systemToast = showSystemToast(duration);
            return;
        }

        List<Activity> activities = UtilsBridge.getActivityList();
        int index = showCount.getAndIncrement();
        boolean anyShown = false;

        for (Activity activity : activities) {
            if (!UtilsBridge.isActivityAlive(activity)) continue;
            if (!anyShown) {
                systemToast = showInWindow(activity, duration);
                anyShown = true;
            } else {
                addViewToast(activity, index, true);
            }
        }

        if (anyShown) {
            registerLifecycleCallback(index);
            int delay = (duration == Toast.LENGTH_SHORT) ? 2000 : 3500;
            UtilsBridge.runOnUiThreadDelayed(this::cancel, delay);
        } else {
            systemToast = showSystemToast(duration);
        }
    }

    @Override
    public void cancel() {
        if (isShowing()) {
            unregisterLifecycleCallback();
            int index = showCount.get() - 1;
            for (Activity activity : UtilsBridge.getActivityList()) {
                if (!UtilsBridge.isActivityAlive(activity)) continue;
                removeViewToast(activity, index);
            }
        }
        if (systemToast != null) {
            systemToast.cancel();
            systemToast = null;
        }
        super.cancel();
    }

    private IToast showSystemToast(int duration) {
        IToast toast = new SystemToast(mToastUtils);
        toast.setRawToast(mToast);
        toast.show(duration);
        return toast;
    }

    private IToast showInWindow(Activity activity, int duration) {
        IToast toast = new WindowManagerToast(
            mToastUtils,
            activity.getWindowManager(),
            WindowManager.LayoutParams.LAST_APPLICATION_WINDOW
        );
        toast.setToastView(getToastViewSnapshot(-1));
        toast.setRawToast(mToast);
        toast.show(duration);
        return toast;
    }

    private void addViewToast(Activity activity, int index, boolean animate) {
        Window window = activity.getWindow();
        if (window == null) return;

        ViewGroup decor = (ViewGroup) window.getDecorView();
        View snapshot = getToastViewSnapshot(index);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = mToast.getGravity();
        lp.leftMargin = mToast.getXOffset();
        lp.topMargin = mToast.getYOffset() + UtilsBridge.getStatusBarHeight();
        lp.bottomMargin = mToast.getYOffset() + UtilsBridge.getNavBarHeight();

        if (animate) {
            snapshot.setAlpha(0f);
            snapshot.animate().alpha(1f).setDuration(200).start();
        }
        decor.addView(snapshot, lp);
    }

    private void removeViewToast(Activity activity, int index) {
        Window window = activity.getWindow();
        if (window == null) return;

        ViewGroup decor = (ViewGroup) window.getDecorView();
        View view = decor.findViewWithTag(TAG_TOAST + index);
        if (view != null) {
            decor.removeView(view);
        }
    }

    private void registerLifecycleCallback(final int index) {
        lifecycleCallback = new UtilsBridge.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity) {
                if (isShowing() && UtilsBridge.isActivityAlive(activity)) {
                    addViewToast(activity, index, false);
                }
            }
        };
        UtilsBridge.addActivityLifecycleCallbacks(lifecycleCallback);
    }

    private void unregisterLifecycleCallback() {
        UtilsBridge.removeActivityLifecycleCallbacks(lifecycleCallback);
        lifecycleCallback = null;
    }

    private boolean isShowing() {
        return lifecycleCallback != null;
    }
}
