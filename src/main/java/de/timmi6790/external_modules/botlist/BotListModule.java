package de.timmi6790.external_modules.botlist;

import de.timmi6790.discord_framework.DiscordBot;
import de.timmi6790.discord_framework.modules.AbstractModule;
import lombok.EqualsAndHashCode;
import org.apache.commons.configuration2.Configuration;
import org.discordbots.api.client.DiscordBotListAPI;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@EqualsAndHashCode(callSuper = true)
public class BotListModule extends AbstractModule {
    private ScheduledFuture<?> updateTask;

    public BotListModule() {
        super("BotList");
    }

    @Override
    public void onEnable() {
        // Bot list server count update task
        final Configuration config = DiscordBot.getConfig();
        if (!config.getString("discord.discordListToken").isEmpty()) {
            final DiscordBotListAPI botListAPI = new DiscordBotListAPI.Builder()
                    .token(config.getString("discord.discordListToken"))
                    .botId(DiscordBot.getDiscord().getSelfUser().getId())
                    .build();

            this.updateTask = Executors.newScheduledThreadPool(1)
                    .scheduleAtFixedRate(
                            () -> botListAPI.setStats(DiscordBot.getDiscord().getGuilds().size()),
                            0,
                            30,
                            TimeUnit.MINUTES
                    );
        }
    }

    @Override
    public void onDisable() {
        this.updateTask.cancel(true);
    }
}
