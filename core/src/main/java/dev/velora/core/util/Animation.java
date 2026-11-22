package dev.velora.core.util;

/**
 * Frame-rate independent animation between a start and target value with an easing curve.
 * Uses wall-clock time, so it behaves identically at 30 or 300 FPS and costs nothing when finished.
 */
public final class Animation {
    private final long durationMs;
    private final Easing easing;
    private double from;
    private double to;
    private long startTime;
    private boolean finished = true;

    public Animation(long durationMs, Easing easing, double initial) {
        this.durationMs = Math.max(1, durationMs);
        this.easing = easing;
        this.from = initial;
        this.to = initial;
    }

    /** Starts animating towards {@code target} from the current interpolated value (no jumps). */
    public void animateTo(double target) {
        if (target == to && !finished) return;
        if (target == to && finished && get() == target) return;
        this.from = get();
        this.to = target;
        this.startTime = System.currentTimeMillis();
        this.finished = false;
    }

    /** Jumps directly to a value without animation. */
    public void set(double value) {
        this.from = value;
        this.to = value;
        this.finished = true;
    }

    public double get() {
        if (finished) return to;
        double t = (System.currentTimeMillis() - startTime) / (double) durationMs;
        if (t >= 1) {
            finished = true;
            return to;
        }
        return from + (to - from) * easing.apply(Math.max(0, t));
    }

    public float getFloat() { return (float) get(); }

    public double getTarget() { return to; }

    public boolean isFinished() {
        get();
        return finished;
    }
}
