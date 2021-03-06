package de.timmi6790.discord_framework.modules.emote_reaction;

import de.timmi6790.discord_framework.modules.emote_reaction.emotereactions.AbstractEmoteReaction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
public class EmoteReactionMessage {
    private final Map<String, AbstractEmoteReaction> emotes;
    private final Set<Long> users;
    private final long channelId;
    private final int deleteTime;
    private final boolean oneTimeUse;
    private final boolean deleteMessage;

    public EmoteReactionMessage(final Map<String, AbstractEmoteReaction> emotes, final Long userId, final long channelId, final int deleteTime) {
        this(emotes, userId, channelId, deleteTime, true, true);
    }

    public EmoteReactionMessage(final Map<String, AbstractEmoteReaction> emotes, final Long userId, final long channelId) {
        this(emotes, userId, channelId, 300, true, true);
    }

    public EmoteReactionMessage(final Map<String, AbstractEmoteReaction> emotes, final Long userId, final long channelId, final int deleteTime, final boolean oneTimeUse, final boolean deleteMessage) {
        this.emotes = emotes;
        this.users = new HashSet<>();
        this.users.add(userId);
        this.channelId = channelId;
        this.deleteTime = deleteTime;
        this.oneTimeUse = oneTimeUse;
        this.deleteMessage = deleteMessage;
    }
}
