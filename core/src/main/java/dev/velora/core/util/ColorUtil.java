package dev.velora.core.util;

public final class ColorUtil {
    private ColorUtil() { }

    public static int alpha(int argb) { return (argb >>> 24) & 0xFF; }
    public static int red(int argb) { return (argb >> 16) & 0xFF; }
    public static int green(int argb) { return (argb >> 8) & 0xFF; }
    public static int blue(int argb) { return argb & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    /** Multiplies the existing alpha by {@code factor} (0..1). Used for fade animations. */
    public static int fade(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
    }

    public static int lerp(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t));
    }

    public static int brighten(int argb, float amount) {
        return lerp(argb, withAlpha(0xFFFFFF, alpha(argb)), amount);
    }

    public static int darken(int argb, float amount) {
        return lerp(argb, withAlpha(0x000000, alpha(argb)), amount);
    }

    /** HSB → opaque ARGB (h, s, b in 0..1). */
    public static int hsb(float h, float s, float b) {
        h = h - (float) Math.floor(h);
        s = Math.max(0, Math.min(1, s));
        b = Math.max(0, Math.min(1, b));
        int r = 0, g = 0, bl = 0;
        if (s == 0) {
            r = g = bl = (int) (b * 255f + 0.5f);
        } else {
            float sector = h * 6f;
            float f = sector - (float) Math.floor(sector);
            float p = b * (1f - s);
            float q = b * (1f - s * f);
            float t = b * (1f - s * (1f - f));
            switch ((int) sector) {
                case 0 -> { r = (int) (b * 255 + .5f); g = (int) (t * 255 + .5f); bl = (int) (p * 255 + .5f); }
                case 1 -> { r = (int) (q * 255 + .5f); g = (int) (b * 255 + .5f); bl = (int) (p * 255 + .5f); }
                case 2 -> { r = (int) (p * 255 + .5f); g = (int) (b * 255 + .5f); bl = (int) (t * 255 + .5f); }
                case 3 -> { r = (int) (p * 255 + .5f); g = (int) (q * 255 + .5f); bl = (int) (b * 255 + .5f); }
                case 4 -> { r = (int) (t * 255 + .5f); g = (int) (p * 255 + .5f); bl = (int) (b * 255 + .5f); }
                default -> { r = (int) (b * 255 + .5f); g = (int) (p * 255 + .5f); bl = (int) (q * 255 + .5f); }
            }
        }
        return argb(255, r, g, bl);
    }

    /** ARGB → float[]{h, s, b}. */
    public static float[] toHsb(int argb) {
        int r = red(argb), g = green(argb), b = blue(argb);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float brightness = max / 255f;
        float saturation = max == 0 ? 0 : (max - min) / (float) max;
        float hue;
        if (saturation == 0) {
            hue = 0;
        } else {
            float rc = (max - r) / (float) (max - min);
            float gc = (max - g) / (float) (max - min);
            float bc = (max - b) / (float) (max - min);
            if (r == max) hue = bc - gc;
            else if (g == max) hue = 2f + rc - bc;
            else hue = 4f + gc - rc;
            hue /= 6f;
            if (hue < 0) hue += 1f;
        }
        return new float[]{hue, saturation, brightness};
    }

    /** Time based rainbow color. {@code offsetMs} lets multiple elements form a gradient. */
    public static int rainbow(long periodMs, long offsetMs, float saturation, float brightness) {
        long t = System.currentTimeMillis() + offsetMs;
        float hue = (t % periodMs) / (float) periodMs;
        return hsb(hue, saturation, brightness);
    }

    /** Green → yellow → red depending on a 0..1 fraction (1 = green). */
    public static int healthColor(float fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        return hsb(fraction / 3f, 0.85f, 0.95f);
    }

    private static int clamp(int c) { return Math.max(0, Math.min(255, c)); }
}
