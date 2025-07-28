public final class BatteryUtils {

    @IntDef({BatteryStatus.UNKNOWN, BatteryStatus.DISCHARGING, BatteryStatus.CHARGING,
            BatteryStatus.NOT_CHARGING, BatteryStatus.FULL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface BatteryStatus {
        int UNKNOWN = BatteryManager.BATTERY_STATUS_UNKNOWN;
        int DISCHARGING = BatteryManager.BATTERY_STATUS_DISCHARGING;
        int CHARGING = BatteryManager.BATTERY_STATUS_CHARGING;
        int NOT_CHARGING = BatteryManager.BATTERY_STATUS_NOT_CHARGING;
        int FULL = BatteryManager.BATTERY_STATUS_FULL;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isIgnoringBatteryOptimizations() {
        return isIgnoringBatteryOptimizations(Utils.getApp().getPackageName());
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isIgnoringBatteryOptimizations(String pkgName) {
        try {
            PowerManager pm = (PowerManager) Utils.getApp().getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(pkgName);
        } catch (Exception e) {
            return true;
        }
    }

    public static void registerBatteryStatusChangedListener(OnBatteryStatusChangedListener l) {
        BatteryChangedReceiver.getInstance().register(l);
    }

    public static boolean isRegistered(OnBatteryStatusChangedListener l) {
        return BatteryChangedReceiver.getInstance().isRegistered(l);
    }

    public static void unregisterBatteryStatusChangedListener(OnBatteryStatusChangedListener l) {
        BatteryChangedReceiver.getInstance().unregister(l);
    }

    public interface OnBatteryStatusChangedListener {
        void onBatteryStatusChanged(Status status);
    }

    public static final class Status {
        private final int level;
        @BatteryStatus
        private final int status;

        Status(int level, int status) {
            this.level = level;
            this.status = status;
        }

        public int getLevel() {
            return level;
        }

        @BatteryStatus
        public int getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return batteryStatus2String(status) + ": " + level + '%';
        }

        public static String batteryStatus2String(@BatteryStatus int status) {
            if (status == BatteryStatus.DISCHARGING) return "discharging";
            if (status == BatteryStatus.CHARGING) return "charging";
            if (status == BatteryStatus.NOT_CHARGING) return "not_charging";
            if (status == BatteryStatus.FULL) return "full";
            return "unknown";
        }
    }

    static final class BatteryChangedReceiver extends BroadcastReceiver {

        private static BatteryChangedReceiver getInstance() {
            return LazyHolder.INSTANCE;
        }

        private final Set<OnBatteryStatusChangedListener> listeners = new HashSet<>();
        private boolean receiverRegistered = false;

        void register(OnBatteryStatusChangedListener l) {
            if (l == null) return;
            ThreadUtils.runOnUiThread(() -> {
                if (listeners.add(l)) updateRegistration();
            });
        }

        boolean isRegistered(OnBatteryStatusChangedListener l) {
            return l != null && listeners.contains(l);
        }

        void unregister(OnBatteryStatusChangedListener l) {
            if (l == null) return;
            ThreadUtils.runOnUiThread(() -> {
                if (listeners.remove(l)) updateRegistration();
            });
        }

        private void updateRegistration() {
            if (!receiverRegistered && !listeners.isEmpty()) {
                IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Utils.getApp().registerReceiver(this, f);
                receiverRegistered = true;
            } else if (receiverRegistered && listeners.isEmpty()) {
                Utils.getApp().unregisterReceiver(this);
                receiverRegistered = false;
            }
        }

        @Override
        public void onReceive(Context c, Intent intent) {
            if (!Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int st = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryStatus.UNKNOWN);
            Status status = new Status(level, st);
            ThreadUtils.runOnUiThread(() -> listeners.forEach(l -> l.onBatteryStatusChanged(status)));
        }

        private static final class LazyHolder {
            private static final BatteryChangedReceiver INSTANCE = new BatteryChangedReceiver();
        }
    }
}
