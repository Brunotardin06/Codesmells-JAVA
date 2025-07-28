package com.example.permissions;   // ajuste se necessário

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Delegate que lida com permissões especiais em uma {@link UtilsTransActivity}.
 * Divergent Change remanescente apenas em {@link #onCreated} (switch de tipos).
 */
@RequiresApi(api = Build.VERSION_CODES.M)
final class PermissionActivityImpl extends UtilsTransActivity.TransActivityDelegate {

    /* ---------- tipos de requisição ------------------------------------------------------- */
    private static final String TYPE                = "TYPE";
    private static final int    TYPE_RUNTIME        = 0x01;
    private static final int    TYPE_WRITE_SETTINGS = 0x02;
    private static final int    TYPE_DRAW_OVERLAYS  = 0x03;

    /* ---------- estado global ------------------------------------------------------------- */
    private static int currentRequestCode = -1;
    private static final PermissionActivityImpl INSTANCE = new PermissionActivityImpl();

    /* callbacks externos (devem ser preenchidos por PermissionUtils ou similar) */
    private static SimpleCallback sSimpleCallback4WriteSettings;
    private static SimpleCallback sSimpleCallback4DrawOverlays;

    /* referência ao objeto utilitário (fornecida por PermissionUtils) */
    static PermissionUtils sInstance;

    /* ---------- bootstrap ----------------------------------------------------------------- */
    static void start(int type) {
        UtilsTransActivity.start(i -> i.putExtra(TYPE, type), INSTANCE);
    }

    /* -------------------------------------------------------------------------------------- */
    /* Ciclo de vida                                                                          */
    /* -------------------------------------------------------------------------------------- */

    @Override
    public void onCreated(@NonNull UtilsTransActivity act, @Nullable Bundle b) {
        act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                               | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

        switch (act.getIntent().getIntExtra(TYPE, -1)) {
            case TYPE_RUNTIME        -> handleRuntime(act);

            case TYPE_WRITE_SETTINGS -> {
                currentRequestCode = TYPE_WRITE_SETTINGS;
                startWriteSettingsActivity(act, TYPE_WRITE_SETTINGS);
            }

            case TYPE_DRAW_OVERLAYS  -> {
                currentRequestCode = TYPE_DRAW_OVERLAYS;
                startOverlayPermissionActivity(act, TYPE_DRAW_OVERLAYS);
            }

            default -> {
                Log.e("PermissionUtils", "type is wrong.");
                act.finish();
            }
        }
    }

    /* -------------------------------------------------------------------------------------- */
    /* Helpers                                                                                */
    /* -------------------------------------------------------------------------------------- */

    private void handleRuntime(UtilsTransActivity act) {
        if (sInstance == null
            || sInstance.mPermissionsRequest == null
            || sInstance.mPermissionsRequest.isEmpty()) {
            Log.e("PermissionUtils", "runtime params invalid");
            act.finish();
            return;
        }

        if (sInstance.mThemeCallback != null) {
            sInstance.mThemeCallback.onActivityCreate(act);
        }

        if (sInstance.mOnExplainListener != null) {
            sInstance.mOnExplainListener.explain(
                    act,
                    sInstance.mPermissionsRequest,
                    start -> { if (start) requestPermissions(act); else act.finish(); }
            );
            sInstance.mOnExplainListener = null;
        } else {
            requestPermissions(act);
        }
    }

    private void requestPermissions(UtilsTransActivity act) {
        Runnable req = () -> act.requestPermissions(
                sInstance.mPermissionsRequest.toArray(new String[0]), 1);
        if (!sInstance.shouldRationale(act, req)) req.run();
    }

    /* -------------------------------------------------------------------------------------- */
    /* Framework overrides                                                                    */
    /* -------------------------------------------------------------------------------------- */

    @Override
    public void onRequestPermissionsResult(@NonNull UtilsTransActivity a,
                                           int rq,
                                           @NonNull String[] p,
                                           @NonNull int[] g) {
        a.finish();
        if (sInstance != null && sInstance.mPermissionsRequest != null) {
            sInstance.onRequestPermissionsResult(a);
        }
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull UtilsTransActivity a, MotionEvent e) {
        a.finish();
        return true;
    }

    @Override
    public void onDestroy(@NonNull UtilsTransActivity a) {
        if (currentRequestCode != -1) {
            checkRequestCallback(currentRequestCode);
            currentRequestCode = -1;
        }
        super.onDestroy(a);
    }

    @Override
    public void onActivityResult(@NonNull UtilsTransActivity a,
                                 int r, int c, Intent d) {
        a.finish();
    }

    /* -------------------------------------------------------------------------------------- */
    /* checkRequestCallback – refatorado (livre de Divergent Change)                          */
    /* -------------------------------------------------------------------------------------- */

    /** Estrutura de despacho de callbacks. */
    private static final class PermInfo {
        final Supplier<SimpleCallback> cb;
        final BooleanSupplier grantedCheck;
        final Runnable cleaner;
        PermInfo(Supplier<SimpleCallback> cb,
                 BooleanSupplier grantedCheck,
                 Runnable cleaner) {
            this.cb = cb;
            this.grantedCheck = grantedCheck;
            this.cleaner = cleaner;
        }
    }

    /** Tabela estática — adicione novas permissões aqui, sem tocar no método. */
    private static final Map<Integer, PermInfo> PERM_TABLE = new HashMap<>();
    static {
        PERM_TABLE.put(
                TYPE_WRITE_SETTINGS,
                new PermInfo(
                        () -> sSimpleCallback4WriteSettings,
                        PermissionActivityImpl::isGrantedWriteSettings,
                        () -> sSimpleCallback4WriteSettings = null));

        PERM_TABLE.put(
                TYPE_DRAW_OVERLAYS,
                new PermInfo(
                        () -> sSimpleCallback4DrawOverlays,
                        PermissionActivityImpl::isGrantedDrawOverlays,
                        () -> sSimpleCallback4DrawOverlays = null));
    }

    /** Método estável: não muda quando novas permissões são adicionadas. */
    private void checkRequestCallback(int requestCode) {
        PermInfo info = PERM_TABLE.get(requestCode);
        if (info == null) return;               // tipo não mapeado

        SimpleCallback cb = info.cb.get();
        if (cb == null) return;                 // callback ausente

        if (info.grantedCheck.getAsBoolean()) cb.onGranted();
        else                                   cb.onDenied();

        info.cleaner.run();                     // limpa referência
    }

    /* -------------------------------------------------------------------------------------- */
    /* Stubs utilitários – implemente conforme sua base de código                             */
    /* -------------------------------------------------------------------------------------- */

    private static boolean isGrantedWriteSettings() {
        // Implementação real deveria usar Settings.System.canWrite(...)
        return android.provider.Settings.System.canWrite(UtilsApp.getApp());
    }

    private static boolean isGrantedDrawOverlays() {
        // Implementação real deveria usar Settings.canDrawOverlays(...)
        return android.provider.Settings.canDrawOverlays(UtilsApp.getApp());
    }

    private void startWriteSettingsActivity(UtilsTransActivity act, int requestCode) {
        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(android.net.Uri.parse("package:" + act.getPackageName()));
        act.startActivityForResult(intent, requestCode);
    }

    private void startOverlayPermissionActivity(UtilsTransActivity act, int requestCode) {
        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(android.net.Uri.parse("package:" + act.getPackageName()));
        act.startActivityForResult(intent, requestCode);
    }

    /* -------------------------------------------------------------------------------------- */
    /* Tipos auxiliares externos (place‑holders)                                              */
    /* -------------------------------------------------------------------------------------- */

    interface SimpleCallback { void onGranted(); void onDenied(); }

    static class UtilsApp { static android.content.Context getApp() { return null; } }

    /** Coloque sua classe PermissionUtils real aqui ou ajuste as referências. */
    static class PermissionUtils {
        java.util.List<String> mPermissionsRequest;
        ThemeCallback mThemeCallback;
        ExplainListener mOnExplainListener;

        boolean shouldRationale(UtilsTransActivity act, Runnable req) { return false; }

        void onRequestPermissionsResult(UtilsTransActivity a) {}
        interface ThemeCallback { void onActivityCreate(UtilsTransActivity a); }
        interface ExplainListener {
            void explain(UtilsTransActivity a, java.util.List<String> p, java.util.function.Consumer<Boolean> c);
        }
    }
}
