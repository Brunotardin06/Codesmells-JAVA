package com.blankj.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ThreadUtils: simplified and clean thread and scheduling utilities.
 * <p>
 * Provides:
 * - Main thread execution helpers
 * - Fixed-size thread pools for CPU and IO tasks
 * - Cached and single-thread executors
 * - Fluent scheduling API via ScheduleBuilder
 */
public final class ThreadUtils {
    private ThreadUtils() { /* Prevent instantiation */ }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService CPU_POOL = Executors.newFixedThreadPool(CPU_COUNT);
    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(CPU_COUNT * 2);
    private static final ExecutorService CACHED_POOL = Executors.newCachedThreadPool();
    private static final ExecutorService SINGLE_POOL = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);

    /**
     * Checks if current thread is the main (UI) thread.
     */
    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /**
     * Runs a task immediately on the main thread.
     */
    public static void runOnUiThread(Runnable task) {
        if (isMainThread()) {
            task.run();
        } else {
            MAIN_HANDLER.post(task);
        }
    }

    /**
     * Runs a task on the main thread after the specified delay.
     */
    public static void runOnUiThreadDelayed(Runnable task, long delayMillis) {
        MAIN_HANDLER.postDelayed(task, delayMillis);
    }

    /** Returns a thread pool optimized for CPU-bound tasks. */
    public static ExecutorService cpuPool() { return CPU_POOL; }
    /** Returns a thread pool optimized for IO-bound tasks. */
    public static ExecutorService ioPool() { return IO_POOL; }
    /** Returns a cached thread pool for lightweight asynchronous tasks. */
    public static ExecutorService cachedPool() { return CACHED_POOL; }
    /** Returns a single-thread executor for sequential tasks. */
    public static ExecutorService singlePool() { return SINGLE_POOL; }

    /** Executes a task in the CPU pool. */
    public static void executeCpu(Runnable task) { CPU_POOL.execute(task); }
    /** Executes a task in the IO pool. */
    public static void executeIo(Runnable task) { IO_POOL.execute(task); }
    /** Executes a task in the cached pool. */
    public static void executeCached(Runnable task) { CACHED_POOL.execute(task); }
    /** Executes a task in the single-thread pool. */
    public static void executeSingle(Runnable task) { SINGLE_POOL.execute(task); }

    /**
     * Entry point for scheduling tasks with a fluent API.
     * @param task the Runnable to schedule
     * @return a ScheduleBuilder for configuration
     */
    public static ScheduleBuilder schedule(Runnable task) {
        return new ScheduleBuilder(task);
    }

    /**
     * Builder for delayed or periodic task scheduling.
     */
    public static class ScheduleBuilder {
        private final Runnable task;
        private long initialDelay = 0;
        private long period = -1;
        private TimeUnit unit = TimeUnit.MILLISECONDS;

        private ScheduleBuilder(Runnable task) {
            this.task = task;
        }

        /** @param delay delay before first execution */
        public ScheduleBuilder delay(long delay, TimeUnit unit) {
            this.initialDelay = delay;
            this.unit = unit;
            return this;
        }

        /** @param period interval between executions (fixed-rate) */
        public ScheduleBuilder period(long period, TimeUnit unit) {
            this.period = period;
            this.unit = unit;
            return this;
        }

        /**
         * Starts the scheduled task. If period &lt; 0, schedules a one-shot; otherwise fixed-rate.
         * @return ScheduledFuture representing pending completion
         */
        public ScheduledFuture<?> start() {
            if (period < 0) {
                return SCHEDULER.schedule(task, initialDelay, unit);
            } else {
                return SCHEDULER.scheduleAtFixedRate(task, initialDelay, period, unit);
            }
        }
    }
}
