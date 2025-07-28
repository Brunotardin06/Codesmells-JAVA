import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class Delay {

    private static final Duration DEFAULT_LOWER_BOUND = Duration.ZERO;
    private static final Duration DEFAULT_UPPER_BOUND = Duration.ofSeconds(30);
    private static final int DEFAULT_POWER_OF = 2;
    private static final TimeUnit DEFAULT_TIMEUNIT = TimeUnit.MILLISECONDS;

    protected Delay() { }

    public abstract Duration createDelay(long attempt);

    public static Delay constant(Duration delay) {
        LettuceAssert.notNull(delay, "Delay must not be null");
        LettuceAssert.isTrue(delay.toNanos() >= 0, "Delay must be greater or equal to 0");
        return new ConstantDelay(delay);
    }

    public static Delay exponential(Duration lower, Duration upper, int powersOf, TimeUnit targetTimeUnit) {
        LettuceAssert.notNull(lower, "Lower boundary must not be null");
        LettuceAssert.isTrue(lower.toNanos() >= 0, "Lower boundary must be greater or equal to 0");
        LettuceAssert.notNull(upper, "Upper boundary must not be null");
        LettuceAssert.isTrue(upper.toNanos() > lower.toNanos(), "Upper boundary must be greater than the lower boundary");
        LettuceAssert.isTrue(powersOf > 1, "PowersOf must be greater than 1");
        LettuceAssert.notNull(targetTimeUnit, "Target TimeUnit must not be null");
        return new ExponentialDelay(lower, upper, powersOf, targetTimeUnit);
    }

    public static Delay exponential(ExponentialOpts opts) {
        return exponential(opts.lower(), opts.upper(), opts.powersOf(), opts.unit());
    }

    public static Delay exponential() {
        return exponential(DEFAULT_LOWER_BOUND, DEFAULT_UPPER_BOUND, DEFAULT_POWER_OF, DEFAULT_TIMEUNIT);
    }

    public static Delay equalJitter(JitterOpts opts) {
        return new EqualJitterDelay(opts.lower(), opts.upper(), opts.base(), opts.unit());
    }

    public static Delay fullJitter(JitterOpts opts) {
        return new FullJitterDelay(opts.lower(), opts.upper(), opts.base(), opts.unit());
    }

    public static Supplier<Delay> decorrelatedJitter(JitterOpts opts) {
        return () -> new DecorrelatedJitterDelay(opts.lower(), opts.upper(), opts.base(), opts.unit());
    }

    public static long randomBetween(long min, long max) {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    protected static Duration applyBounds(Duration calculatedValue, Duration lower, Duration upper) {
        if (calculatedValue.compareTo(lower) < 0) {
            return lower;
        }
        if (calculatedValue.compareTo(upper) > 0) {
            return upper;
        }
        return calculatedValue;
    }

    public interface StatefulDelay {
        void reset();
    }

    public record ExponentialOpts(Duration lower, Duration upper, int powersOf, TimeUnit unit) {
        public ExponentialOpts {
            LettuceAssert.notNull(lower, "lower must not be null");
            LettuceAssert.notNull(upper, "upper must not be null");
            LettuceAssert.notNull(unit, "unit must not be null");
            LettuceAssert.isTrue(lower.toNanos() >= 0, "lower must be >= 0");
            LettuceAssert.isTrue(upper.toNanos() > lower.toNanos(), "upper must be > lower");
            LettuceAssert.isTrue(powersOf > 1, "powersOf must be > 1");
        }
    }

    public record JitterOpts(Duration lower, Duration upper, long base, TimeUnit unit) {
        public JitterOpts {
            LettuceAssert.notNull(lower, "lower must not be null");
            LettuceAssert.notNull(upper, "upper must not be null");
            LettuceAssert.notNull(unit, "unit must not be null");
            LettuceAssert.isTrue(lower.toNanos() >= 0, "lower must be >= 0");
            LettuceAssert.isTrue(upper.toNanos() > lower.toNanos(), "upper must be > lower");
            LettuceAssert.isTrue(base >= 1, "base must be >= 1");
        }
    }
}
