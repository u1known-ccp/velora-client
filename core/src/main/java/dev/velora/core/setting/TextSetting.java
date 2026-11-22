package dev.velora.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String id, String name, String description, String defaultValue, int maxLength) {
        super(id, name, description, defaultValue);
        this.maxLength = Math.max(1, maxLength);
    }

    public int getMaxLength() { return maxLength; }

    @Override
    protected String sanitize(String input) {
        if (input == null) return getDefault();
        // strip control characters (also blocks section-sign color code abuse in chat macros)
        StringBuilder sb = new StringBuilder(Math.min(input.length(), maxLength));
        for (int i = 0; i < input.length() && sb.length() < maxLength; i++) {
            char c = input.charAt(i);
            if (c >= 0x20 && c != 0x7F && c != '§') sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsString());
        else reset();
    }
}
