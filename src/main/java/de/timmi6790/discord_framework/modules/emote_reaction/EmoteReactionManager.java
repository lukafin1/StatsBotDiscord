package de.timmi6790.discord_framework.modules.emote_reaction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import de.timmi6790.discord_framework.DiscordBot;
import de.timmi6790.discord_framework.modules.event_handler.SubscribeEvent;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EmoteReactionManager {
    private static final int ACTIVE_EMOTES_LIMIT = 6;

    @Getter
    private final Map<Long, AtomicInteger> activeEmotesPerPlayer = new ConcurrentHashMap<>();

    @Getter
    private final Cache<Long, EmoteReactionMessage> emoteMessageCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(new Expiry<Long, EmoteReactionMessage>() {
                @Override
                public long expireAfterCreate(@NonNull final Long key, @NonNull final EmoteReactionMessage value, final long currentTime) {
                    return TimeUnit.SECONDS.toNanos(value.getDeleteTime());
                }

                @Override
                public long expireAfterUpdate(@NonNull final Long key, @NonNull final EmoteReactionMessage value, final long currentTime, @NonNegative final long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(@NonNull final Long key, @NonNull final EmoteReactionMessage value, final long currentTime, @NonNegative final long currentDuration) {
                    return currentDuration;
                }
            })
            .removalListener((key, value, cause) -> {
                if (key == null || value == null) {
                    return;
                }

                // Deduct the active emotes per player
                value.getUsers()
                        .stream()
                        .filter(this.activeEmotesPerPlayer::containsKey)
                        .forEach(user -> {
                            AtomicInteger currentCount = this.activeEmotesPerPlayer.get(user);
                            if (currentCount.get() > 1) {
                                currentCount.decrementAndGet();

                            } else {
                                this.activeEmotesPerPlayer.remove(user);
                            }
                        });

                final MessageChannel channel = DiscordBot.getDiscord().getTextChannelById(value.getChannelId());
                if (channel == null) {
                    return;
                }

                channel.retrieveMessageById(key)
                        .queue(message -> value.getEmotes()
                                        .keySet()
                                        .forEach(emote -> {
                                            try {
                                                message.removeReaction(emote).queue();
                                            } catch (final Exception ignore) {
                                            }
                                        }),
                                failure -> {
                                }
                        );
            })
            .build();

    public EmoteReactionManager() {
        DiscordBot.getEventManager().addEventListener(this);
    }

    public void addEmoteReactionMessage(final Message message, final EmoteReactionMessage emoteReactionMessage) {
        // Remove all players who reached the rate limit
        emoteReactionMessage.getUsers()
                .removeIf(user -> {
                    final AtomicInteger currentActive = this.activeEmotesPerPlayer.computeIfAbsent(user, k -> new AtomicInteger(0));
                    if (currentActive.get() >= ACTIVE_EMOTES_LIMIT) {
                        return true;
                    }

                    currentActive.incrementAndGet();
                    return false;
                });

        if (emoteReactionMessage.getUsers().isEmpty()) {
            return;
        }

        this.emoteMessageCache.put(message.getIdLong(), emoteReactionMessage);
        emoteReactionMessage.getEmotes()
                .keySet()
                .forEach(emote -> {
                    try {
                        message.addReaction(emote).queue();
                    } catch (final Exception ignore) {
                    }
                });
    }

    @SubscribeEvent
    public void onReactionAdd(final MessageReactionAddEvent event) {
        final EmoteReactionMessage emoteReactionMessage = this.emoteMessageCache.getIfPresent(event.getMessageIdLong());
        if (emoteReactionMessage == null
                || !emoteReactionMessage.getUsers().contains(event.getUserIdLong())
                || !emoteReactionMessage.getEmotes().containsKey(event.getReaction().getReactionEmote().getName())) {
            return;
        }

        if (emoteReactionMessage.isDeleteMessage()) {
            final MessageChannel channel = DiscordBot.getDiscord().getTextChannelById(emoteReactionMessage.getChannelId());
            if (channel != null) {
                channel.deleteMessageById(event.getMessageIdLong()).queue();
            }

        } else if (emoteReactionMessage.isOneTimeUse()) {
            this.emoteMessageCache.invalidate(event.getMessageIdLong());
        }

        emoteReactionMessage.getEmotes().get(event.getReaction().getReactionEmote().getName()).onEmote();
    }

    @SubscribeEvent
    public void onMessageDelete(final MessageDeleteEvent event) {
        this.emoteMessageCache.invalidate(event.getMessageIdLong());
    }
}
