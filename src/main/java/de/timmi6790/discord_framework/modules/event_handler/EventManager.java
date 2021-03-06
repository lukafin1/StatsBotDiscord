package de.timmi6790.discord_framework.modules.event_handler;

import de.timmi6790.discord_framework.DiscordBot;
import de.timmi6790.discord_framework.datatypes.MapBuilder;
import de.timmi6790.discord_framework.modules.command.AbstractCommand;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import lombok.Data;
import net.dv8tion.jda.api.events.Event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventManager {
    private final Map<Class<Event>, Map<EventPriority, Set<EventObject>>> eventListeners = new ConcurrentHashMap<>();

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public EventManager() {
        DiscordBot.getDiscord().addEventListener(new DiscordEventListener());
    }

    private void callListenerSafe(final EventObject listener, final Event event) {
        try {
            // At the moment all events can't be canceled, that is why we run everything on a different thread
            this.executorService.submit(() -> listener.getMethod().invoke(listener.getObject(), event));
        } catch (final Exception e) {
            e.printStackTrace();

            // Sentry error
            final Map<String, String> data = new MapBuilder<String, String>(HashMap::new)
                    .put("Class", event.getClass().toString())
                    .put("Listener", listener.getMethod().getName())
                    .build();

            final Breadcrumb breadcrumb = new BreadcrumbBuilder()
                    .setCategory("Event")
                    .setData(data)
                    .build();

            final EventBuilder eventBuilder = new EventBuilder()
                    .withMessage("Event Exception")
                    .withLevel(io.sentry.event.Event.Level.ERROR)
                    .withBreadcrumbs(Collections.singletonList(breadcrumb))
                    .withLogger(AbstractCommand.class.getName())
                    .withSentryInterface(new ExceptionInterface(e));

            DiscordBot.getSentry().sendEvent(eventBuilder);
        }
    }

    public void addEventListener(final Object listener) {
        Arrays.stream(listener.getClass().getMethods())
                .filter(method -> method.getParameterCount() == 1)
                .forEach(method -> {
                    final SubscribeEvent annotation;
                    try {
                        annotation = method.getAnnotation(SubscribeEvent.class);
                    } catch (final NullPointerException ignore) {
                        return;
                    }

                    final Class<?> parameter = method.getParameterTypes()[0];
                    if (Event.class.isAssignableFrom(parameter)) {
                        this.eventListeners.computeIfAbsent((Class<Event>) parameter, key -> new ConcurrentHashMap<>())
                                .computeIfAbsent(annotation.priority(), key -> Collections.synchronizedSet(new HashSet<>()))
                                .add(new EventObject(listener, method));
                    }
                });
    }

    public void addEventListeners(final Object... listeners) {
        Arrays.stream(listeners).forEach(this::addEventListener);
    }

    public void removeEventListener(final Object listener) {
        for (final Map<EventPriority, Set<EventObject>> value : this.eventListeners.values()) {
            final Iterator<Map.Entry<EventPriority, Set<EventObject>>> it = value.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<EventPriority, Set<EventObject>> entry = it.next();
                final boolean removedElement = entry.getValue().removeIf(eventListener -> eventListener.getObject().equals(listener));
                if (removedElement) {
                    if (entry.getValue().isEmpty()) {
                        it.remove();
                    }

                    return;
                }
            }
        }
    }

    public void clearEventListener() {
        this.eventListeners.clear();
    }

    public void executeEvent(final Event event) {
        Optional.ofNullable(this.eventListeners.get(event.getClass()))
                .ifPresent(listeners -> Arrays.stream(EventPriority.values())
                        .map(listeners::get)
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .forEach(listener -> this.callListenerSafe(listener, event))
                );
    }

    @Data
    private static class EventObject {
        private final Object object;
        private final Method method;
    }
}
