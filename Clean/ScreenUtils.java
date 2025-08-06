package com.blankj.util;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import static android.Manifest.permission.WRITE_SETTINGS;

/**
 * ScreenUtils: clean and consolidated screen-related utilities.
 */
public final class ScreenUtils {
    private ScreenUtils() {
        throw new UnsupportedOperationException("No instances");
    }

    // -- Common helpers --
    private static WindowManager getWindowManager(Context ctx) {
        return (WindowManager) ctx.getApplicationContext()
                                  .getSystemService(Context.WINDOW_SERVICE);
    }

    private static Display getDisplay(Context ctx) {
        return getWindowManager(ctx).getDefaultDisplay();
    }

    private static DisplayMetrics getMetrics(Context ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        getDisplay(ctx).getMetrics(dm);
        return dm;
    }

    private static Point getRealSize(Context ctx) {
        Point p = new Point();
        Display d = getDisplay(ctx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            d.getRealSize(p);
        } else {
            d.getSize(p);
        }
        return p;
    }

    // -- Screen dimensions --
    public static int getScreenWidth() {
        return getRealSize(Utils.getApp()).x;
    }

    public static int getScreenHeight() {
        return getRealSize(Utils.getApp()).y;
    }

    public static int getAppScreenWidth() {
        return getMetrics(Utils.getApp()).widthPixels;
    }

    public static int getAppScreenHeight() {
        return getMetrics(Utils.getApp()).heightPixels;
    }

    // -- Density --
    public static float getDensity() {
        return getMetrics(Utils.getApp()).density;
    }

    public static int getDensityDpi() {
        return getMetrics(Utils.getApp()).densityDpi;
    }

    public static float getXDpi() {
        return getMetrics(Utils.getApp()).xdpi;
    }

    public static float getYDpi() {
        return getMetrics(Utils.getApp()).ydpi;
    }

    // -- View position and distance --
    public static Point getViewLocationOnScreen(@NonNull View view) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return new Point(loc[0], loc[1]);
    }

    public static int getViewX(@NonNull View view) {
        return getViewLocationOnScreen(view).x;
    }

    public static int getViewY(@NonNull View view) {
        return getViewLocationOnScreen(view).y;
    }

    public static int getDistanceToEdgeX(@NonNull View view) {
        return getScreenWidth() - getViewX(view);
    }

    public static int getDistanceToEdgeY(@NonNull View view) {
        return getScreenHeight() - getViewY(view);
    }

    // -- Fullscreen and orientation --
    public static void setFullScreen(@NonNull Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public static void clearFullScreen(@NonNull Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public static void toggleFullScreen(@NonNull Activity activity) {
        if (isFullScreen(activity)) {
            clearFullScreen(activity);
        } else {
            setFullScreen(activity);
        }
    }

    public static boolean isFullScreen(@NonNull Activity activity) {
        int flag = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        return (activity.getWindow().getAttributes().flags & flag) == flag;
    }

    @SuppressWarnings("SourceLockedOrientationActivity")
    public static void setLandscape(@NonNull Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @SuppressWarnings("SourceLockedOrientationActivity")
    public static void setPortrait(@NonNull Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public static boolean isLandscape() {
        return Utils.getApp().getResources().getConfiguration().orientation
               == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isPortrait() {
        return Utils.getApp().getResources().getConfiguration().orientation
               == Configuration.ORIENTATION_PORTRAIT;
    }

    // -- Rotation --
    public static int getScreenRotation(@NonNull Activity activity) {
        switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:  return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            case Surface.ROTATION_0:
            default:                   return 0;
        }
    }

    // -- Screenshot --
    public static Bitmap screenShot(@NonNull Activity activity, boolean cropStatusBar) {
        View decor = activity.getWindow().getDecorView();
        decor.setDrawingCacheEnabled(true);
        Bitmap bmp = Bitmap.createBitmap(decor.getDrawingCache());
        decor.setDrawingCacheEnabled(false);

        if (cropStatusBar) {
            int statusBar = UtilsBridge.getStatusBarHeight();
            DisplayMetrics dm = getMetrics(activity);
            return Bitmap.createBitmap(bmp, 0, statusBar, 
                                       dm.widthPixels, dm.heightPixels - statusBar);
        }
        return bmp;
    }

    // -- Sleep duration --
    @RequiresPermission(WRITE_SETTINGS)
    public static void setSleepDuration(int millis) {
        Settings.System.putInt(
            Utils.getApp().getContentResolver(),
            Settings.System.SCREEN_OFF_TIMEOUT,
            millis
        );
    }

    public static int getSleepDuration() {
        try {
            return Settings.System.getInt(
                Utils.getApp().getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT
            );
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return -1;
        }
    }

    // -- Keyguard state --
    public static boolean isScreenLocked() {
        KeyguardManager km = (KeyguardManager) Utils.getApp()
            .getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.inKeyguardRestrictedInputMode();
    }
}
