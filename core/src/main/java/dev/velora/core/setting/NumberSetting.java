package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A numeric setting with range and step. Rendered as a slider. */
public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;

    public NumberSetting(String id, String name, String description, double defaultValue, double min, double max, double step) {
        this(id, name, description, defaultValue, min, max, step, "");
    }

    public NumberSetting(String id, String name, String description, double defaultValue, double min, double max, double step, String suffix) {
        super(id, name, description, defaultValue);
        if (min > max) throw new IllegalArgumentException("min > max for " + id);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix == null ? "" : suffix;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public String getSuffix() { return suffix; }

    public double getDouble() { return value; }
    public float getFloat() { return value.floatValue(); }
    public int getInt() { return (int) Math.round(value); }

    /** Position of the value inside the range, 0..1. */
    public double getProgress() { return max == min ? 0 : (value - min) / (max - min); }

    public void setFromProgress(double progress) {
        set(min + Math.max(0, Math.min(1, progress)) * (max - min));
    }

    @Override
    protected Double sanitize(Double input) {
        if (input == null || input.isNaN() || input.isInfinite()) return getDefault();
        double v = Math.max(min, Math.min(max, input));
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            // remove floating point noise like 0.30000000000000004
            v = Math.round(v * 1_000_000d) / 1_000_000d;
        }
        return v;
    }

    public String format() {
        boolean integral = step >= 1 && Math.floor(step) == step;
        String number = integral ? Integer.toString(getInt()) : String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
        return number + suffix;
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            set(element.getAsDouble());
        } else {
            reset();
        }
    }
}
