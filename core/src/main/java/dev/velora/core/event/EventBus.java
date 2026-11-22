package dev.velora.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal typed event bus. No reflection, no annotation scanning: listeners are plain lambdas
 * stored per exact event class, so posting costs one map lookup plus a list iteration.
 * A failing listener is logged and never breaks the other listeners or the game loop.
 */
public final class EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/EventBus");
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        Consumer<Object> wrapped = e -> ((Consumer<E>) listener).accept((E) e);
        List<Consumer<Object>> list = listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        list.add(wrapped);
        return () -> list.remove(wrapped);
    }

    public <E> E post(E event) {
        List<Consumer<Object>> list = listeners.get(event.getClass());
        if (list == null) return event;
        for (Consumer<Object> l : list) {
            try {
                l.accept(event);
            } catch (RuntimeException ex) {
                LOGGER.error("Listener for {} failed", event.getClass().getSimpleName(), ex);
            }
        }
        return event;
    }

    @FunctionalInterface
    public interface Subscription {
        void unsubscribe();
    }
}
