package dev.velora.core.util;

/** Common easing curves, input/output 0..1. */
public enum Easing {
    LINEAR {
        @Override public double apply(double t) { return t; }
    },
    EASE_OUT_CUBIC {
        @Override public double apply(double t) { double p = 1 - t; return 1 - p * p * p; }
    },
    EASE_IN_OUT_CUBIC {
        @Override public double apply(double t) {
            return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
        }
    },
    EASE_OUT_BACK {
        @Override public double apply(double t) {
            double c1 = 1.70158, c3 = c1 + 1;
            return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
        }
    };

    public abstract double apply(double t);
}
