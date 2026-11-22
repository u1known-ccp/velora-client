package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A clickable action in the settings panel (not persisted). */
public class ButtonSetting extends Setting<Runnable> {
    private final String buttonLabel;

    public ButtonSetting(String id, String name, String description, String buttonLabel, Runnable action) {
        super(id, name, description, action);
        this.buttonLabel = buttonLabel;
    }

    public String getButtonLabel() { return buttonLabel; }

    public void press() { if (value != null) value.run(); }

    @Override public void reset() { }

    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }

    @Override public void fromJson(JsonElement element) { }
}
