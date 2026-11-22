package dev.velora.client.gui.widget;

import dev.velora.core.setting.BooleanSetting;
import dev.velora.core.setting.ButtonSetting;
import dev.velora.core.setting.ColorSetting;
import dev.velora.core.setting.KeybindSetting;
import dev.velora.core.setting.ModeSetting;
import dev.velora.core.setting.NumberSetting;
import dev.velora.core.setting.Setting;
import dev.velora.core.setting.TextSetting;

import java.util.ArrayList;
import java.util.List;

public final class SettingWidgets {
    private SettingWidgets() { }

    public static SettingWidget create(Setting<?> setting) {
        if (setting instanceof BooleanSetting b) return new BasicWidgets.BooleanWidget(b);
        if (setting instanceof NumberSetting n) return new BasicWidgets.NumberWidget(n);
        if (setting instanceof ModeSetting m) return new BasicWidgets.ModeWidget(m);
        if (setting instanceof ColorSetting c) return new ColorWidget(c);
        if (setting instanceof KeybindSetting k) return new InputWidgets.KeybindWidget(k);
        if (setting instanceof TextSetting t) return new InputWidgets.TextWidget(t);
        if (setting instanceof ButtonSetting b) return new BasicWidgets.ButtonWidget(b);
        throw new IllegalArgumentException("No widget for " + setting.getClass().getName());
    }

    public static List<SettingWidget> createAll(List<? extends Setting<?>> settings) {
        List<SettingWidget> list = new ArrayList<>(settings.size());
        for (Setting<?> s : settings) list.add(create(s));
        return list;
    }
}
