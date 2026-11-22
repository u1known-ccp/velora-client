package dev.velora.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry of all modules of the running client. */
public final class ModuleManager {
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final List<Module.ModuleListener> listeners = new CopyOnWriteArrayList<>();
    private Runnable dirtyHook = () -> { };

    public <M extends Module> M register(M module) {
        if (modules.containsKey(module.getId())) {
            throw new IllegalArgumentException("Duplicate module id: " + module.getId());
        }
        module.bind(() -> dirtyHook.run(), (m, enabled) -> listeners.forEach(l -> l.onToggled(m, enabled)));
        modules.put(module.getId(), module);
        return module;
    }

    public void setDirtyHook(Runnable hook) { this.dirtyHook = hook == null ? () -> { } : hook; }

    public void addListener(Module.ModuleListener listener) { listeners.add(listener); }

    public List<Module> all() { return Collections.unmodifiableList(new ArrayList<>(modules.values())); }

    public Optional<Module> byId(String id) { return Optional.ofNullable(modules.get(id)); }

    @SuppressWarnings("unchecked")
    public <M extends Module> M get(Class<M> type) {
        for (Module m : modules.values()) {
            if (type.isInstance(m)) return (M) m;
        }
        throw new IllegalStateException("Module not registered: " + type.getName());
    }

    public <M extends Module> List<M> ofType(Class<M> type) {
        List<M> result = new ArrayList<>();
        for (Module m : modules.values()) {
            if (type.isInstance(m)) result.add(type.cast(m));
        }
        return result;
    }

    public List<Module> byCategory(Category category) {
        return modules.values().stream().filter(m -> m.getCategory() == category).toList();
    }

    /** Case-insensitive search over name, id and description; favorites first, then alphabetical. */
    public List<Module> search(String query, Category category, boolean favoritesOnly) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return modules.values().stream()
                .filter(m -> category == null || m.getCategory() == category)
                .filter(m -> !favoritesOnly || m.isFavorite())
                .filter(m -> q.isEmpty()
                        || m.getName().toLowerCase(Locale.ROOT).contains(q)
                        || m.getId().toLowerCase(Locale.ROOT).contains(q)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(Module::isFavorite).reversed()
                        .thenComparing(m -> m.getName().toLowerCase(Locale.ROOT)))
                .toList();
    }
}
