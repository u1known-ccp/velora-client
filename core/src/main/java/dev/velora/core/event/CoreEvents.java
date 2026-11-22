package dev.velora.core.event;

import dev.velora.core.module.Module;

/** Platform independent events posted by the core. */
public final class CoreEvents {
    private CoreEvents() { }

    /** A module was enabled or disabled. */
    public record ModuleToggled(Module module, boolean enabled) { }

    /** Another profile was loaded; all module states have been replaced. */
    public record ProfileSwitched(String profileName) { }

    /** A global client setting changed (accent color, backend url, ...). */
    public record ClientSettingsChanged() { }
}
