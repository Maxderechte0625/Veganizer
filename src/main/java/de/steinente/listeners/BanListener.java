package de.steinente.listeners;

import de.steinente.Veganizer;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class BanListener extends ListenerAdapter {
    @Override
    public void onGuildBan(GuildBanEvent event) {
        if (event.getGuild().getIdLong() == Veganizer.SERVER_ID) {
            final VoiceListener voiceListener = (VoiceListener) event.getJDA().getRegisteredListeners().get(0);
            boolean isOnStage = false;
            User userOnStage = null;

            for (Member value : voiceListener.getActiveMemberMap().values()) {
                isOnStage = value.getUser().getId().equals(event.getUser().getId());
                userOnStage = value.getUser();
            }

            if (isOnStage) {
                AtomicLong stageId = new AtomicLong();
                final User finalUserOnStage = userOnStage;
                event.getGuild().getVoiceStates().forEach(o -> {
                    if (o.getMember().getUser() == finalUserOnStage) {
                        stageId.set(Objects.requireNonNull(o.getChannel()).getIdLong());
                    }
                });
                voiceListener.onUserLeaveStage(event, Objects.requireNonNull(Objects.requireNonNull(
                        voiceListener.getActiveMember(stageId.get()).getVoiceState()).getChannel()).getIdLong());
            }
        }
    }
}
