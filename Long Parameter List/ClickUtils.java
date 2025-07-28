
public final class ClickUtils {

    private static final long DEFAULT_DEBOUNCE = 1_000L;

    private ClickUtils() {
        throw new UnsupportedOperationException("Instantiation not allowed");
    }

    public static void applySingleDebouncing(View view, View.OnClickListener listener) {
        applyDebouncing(new View[]{view}, false, DEFAULT_DEBOUNCE, listener);
    }

    public static void applySingleDebouncing(View view, long duration, View.OnClickListener listener) {
        applyDebouncing(new View[]{view}, false, duration, listener);
    }

    public static void applySingleDebouncing(View[] views, long duration, View.OnClickListener listener) {
        applyDebouncing(views, false, duration, listener);
    }

    public static void applyGlobalDebouncing(View view, View.OnClickListener listener) {
        applyDebouncing(new View[]{view}, true, DEFAULT_DEBOUNCE, listener);
    }

    public static void applyGlobalDebouncing(View view, long duration, View.OnClickListener listener) {
        applyDebouncing(new View[]{view}, true, duration, listener);
    }

    public static void applyGlobalDebouncing(View[] views, long duration, View.OnClickListener listener) {
        applyDebouncing(views, true, duration, listener);
    }

    public static void applyDebouncing(View[] views, boolean global, long duration, View.OnClickListener listener) { 
        if (views == null || listener == null) return;
        Debouncer debouncer = global ? Debouncer.global(duration) : Debouncer.local(duration);
        for (View v : views) {
            if (v != null) v.setOnClickListener(debouncer.wrap(listener));
        }
    }
}
final class Debouncer {

    private static final Object GLOBAL_LOCK = new Object();
    private static volatile boolean GLOBAL_ENABLED = true;

    private final long duration;
    private final boolean global;

    private Debouncer(long duration, boolean global) {
        this.duration = duration;
        this.global = global;
    }

    static Debouncer global(long duration) {
        return new Debouncer(duration, true);
    }

    static Debouncer local(long duration) {
        return new Debouncer(duration, false);
    }

    View.OnClickListener wrap(View.OnClickListener delegate) {
        return v -> {
            if (global) {
                synchronized (GLOBAL_LOCK) {
                    if (GLOBAL_ENABLED) {
                        GLOBAL_ENABLED = false;
                        v.postDelayed(() -> GLOBAL_ENABLED = true, duration);
                        delegate.onClick(v);
                    }
                }
            } else if (UtilsBridge.isValid(v, duration)) {
                delegate.onClick(v);
            }
        };
    }
}

abstract class OnMultiClickListener implements View.OnClickListener {

    private static final long DEFAULT_INTERVAL = 666L;

    private final int triggerCount;
    private final long interval;
    private long lastClick;
    private int clickCount;

    OnMultiClickListener(int triggerCount) {
        this(triggerCount, DEFAULT_INTERVAL);
    }

    OnMultiClickListener(int triggerCount, long interval) {
        this.triggerCount = triggerCount;
        this.interval = interval;
    }

    @Override
    public final void onClick(View v) {
        long now = System.currentTimeMillis();
        if (now - lastClick < interval) {
            clickCount = (clickCount % triggerCount) + 1;
        } else {
            clickCount = 1;
        }
        lastClick = now;
        if (clickCount == triggerCount) {
            onTriggerClick(v);
        } else {
            onBeforeTriggerClick(v, clickCount);
        }
    }

    public abstract void onTriggerClick(View v);

    public abstract void onBeforeTriggerClick(View v, int count);
}

final class Back2HomeFriendly {

    private static final long TIP_DURATION = 2_000L;
    private static long lastClickTime;
    private static int clickCount;

    private Back2HomeFriendly() {
    }

    static void handle(CharSequence tip, long duration, Back2HomeFriendlyListener listener) {
        long now = SystemClock.elapsedRealtime();
        if (Math.abs(now - lastClickTime) < duration) {
            if (++clickCount == 2) {
                UtilsBridge.startHomeActivity();
                listener.dismiss();
                lastClickTime = 0;
            }
        } else {
            clickCount = 1;
            listener.show(tip, duration);
            lastClickTime = now;
        }
    }

    interface Back2HomeFriendlyListener {
        Back2HomeFriendlyListener DEFAULT = new Back2HomeFriendlyListener() {
            @Override
            public void show(CharSequence text, long duration) {
                UtilsBridge.toastShowShort(text);
            }

            @Override
            public void dismiss() {
                UtilsBridge.toastCancel();
            }
        };

        void show(CharSequence text, long duration);

        void dismiss();
    }
}
