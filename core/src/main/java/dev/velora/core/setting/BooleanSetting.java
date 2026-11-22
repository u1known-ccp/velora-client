package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String id, String name, String description, boolean defaultValue) {
        super(id, name, description, defaultValue);
    }

    public boolean isOn() { return Boolean.TRUE.equals(value); }

    public void toggle() { set(!isOn()); }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(isOn()); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            set(element.getAsBoolean());
        } else {
            reset();
        }
    }
}
