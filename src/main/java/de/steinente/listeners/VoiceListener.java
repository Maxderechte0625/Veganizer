package de.steinente.listeners;

import de.steinente.Veganizer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceRequestToSpeakEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSuppressEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VoiceListener extends ListenerAdapter {
    private final Map<Long, EmbedBuilder> trackingEmbedBuilderMap = new HashMap<>();
    private final Map<Long, Member> activeMemberMap = new HashMap<>();
    private final Map<Long, Long> startTimeMap = new HashMap<>();
    private final Map<Long, ScheduledExecutorService> timerMap = new HashMap<>();
    private final Map<Long, Long> ownMessageIdMap = new HashMap<>();
    private final Button summaryButton = new ButtonImpl(
            "summary-button",
            "Add Summary",
            ButtonStyle.PRIMARY,
            false,
            null
    );
    private final Button talkButton = new ButtonImpl(
            "talk-button",
            "Add Talk",
            ButtonStyle.SUCCESS,
            false,
            null
    );
    private final Button voidButton = new ButtonImpl(
            "void-button",
            "Add Void",
            ButtonStyle.SECONDARY,
            false,
            null
    );
    private final Button banButton = new ButtonImpl(
            "ban-button",
            "Ban",
            ButtonStyle.DANGER,
            false,
            null
    );

    @Override
    public void onGuildVoiceRequestToSpeak(GuildVoiceRequestToSpeakEvent event) {
        if (event.getGuild().getIdLong() == Veganizer.SERVER_ID) {
            // On accept invite to stage
            if (!event.getVoiceState().isSuppressed() && null != event.getOldTime()
                    && !event.getMember().getPermissions().contains(Permission.VOICE_MOVE_OTHERS)) {
                final SelfUser bot = event.getJDA().getSelfUser();
                final long stageId = Objects.requireNonNull(event.getVoiceState().getChannel()).getIdLong();
                final Member member = event.getMember();
                final String activeMemberName = null != member.getNickname() ? member.getNickname() : member.getUser().getName();
                Button initialTalkButton = this.talkButton;
                Button initialVoidButton = this.voidButton;

                this.trackingEmbedBuilderMap.put(stageId, new EmbedBuilder()
                        .setTitle(activeMemberName + " joined " + Objects.requireNonNull(event.getVoiceState().getChannel()).getName())
                        .setDescription("Time on Stage: 00:00:00")
                        .setTimestamp(OffsetDateTime.now())
                        .setColor(Veganizer.GREEN)
                        .setThumbnail(Objects.requireNonNull(this.upscaleAvatar(member.getEffectiveAvatarUrl())))
                        .setFooter(bot.getName() + " by Steinente", this.upscaleAvatar(bot.getEffectiveAvatarUrl()))
                        .addField(new MessageEmbed.Field("User", member.getAsMention(), true))
                        .addField(new MessageEmbed.Field("User-ID", member.getId(), true)));
                this.activeMemberMap.put(stageId, member);
                this.startTimeMap.put(stageId, System.currentTimeMillis());
                this.timerMap.put(stageId, Executors.newSingleThreadScheduledExecutor());
                this.ownMessageIdMap.put(stageId, null);

                // If user has talk
                if (member.getRoles().contains(event.getJDA().getRoleById(Veganizer.TALK_ROLE_ID))) {
                    initialTalkButton = this.talkButton.withLabel("Remove Talk");
                }

                // If user in void
                if (member.getRoles().contains(event.getJDA().getRoleById(Veganizer.VOID_ROLE_ID))) {
                    initialVoidButton = this.voidButton.withLabel("Remove Void");
                }

                Objects.requireNonNull(event.getGuild().getTextChannelById(Veganizer.STAGE_TRACKING_CHANNEL_ID)).sendMessageEmbeds(this.trackingEmbedBuilderMap.get(stageId).build())
                        .addActionRow(this.summaryButton, initialTalkButton, initialVoidButton, this.banButton).queue((message -> {
                            this.ownMessageIdMap.put(stageId, message.getIdLong());
                        }));
                this.timerMap.get(stageId).scheduleWithFixedDelay(() -> this.updateTrackingMessageTimer(event, stageId), 5, 5, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void onGuildVoiceSuppress(GuildVoiceSuppressEvent event) {
        if (event.getGuild().getIdLong() == Veganizer.SERVER_ID) {
            if (null != event.getVoiceState().getChannel()) {
                final long stageId = event.getVoiceState().getChannel().getIdLong();
                // On speaker do not speak anymore
                if (event.getMember() == this.activeMemberMap.get(stageId) && event.isSuppressed()) {
                    this.onUserLeaveStage(event, stageId);
                }
            }

        }
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getGuild().getIdLong() == Veganizer.SERVER_ID) {
            // On leave stage
            if (null != event.getOldValue() && event.getOldValue().getName().contains("Stage Debatte")) {
                if (null == event.getNewValue() || event.getNewValue() != event.getOldValue()) {
                    final long stageId = event.getOldValue().getIdLong();
                    if (event.getMember() == this.activeMemberMap.get(stageId)) {
                        this.onUserLeaveStage(event, stageId);
                    }
                }
            }
        }
    }

    /**
     * Tasks after leaving the stage.
     *
     * @param event GenericGuildEvent
     */
    public void onUserLeaveStage(GenericGuildEvent event, long stageId) {
        this.updateTrackingMessageTimer(event, stageId);
        this.trackingEmbedBuilderMap.get(stageId).setColor(Veganizer.RED);
        this.updateTrackingMessageEmbed(event, stageId);
        this.clearVariables(stageId);
    }

    /**
     * Clears all variables.
     */
    public void clearVariables(long stageId) {
        this.timerMap.get(stageId).shutdown();
    }

    /**
     * Getter for TrackingEmbedBuilder.
     *
     * @return TrackingEmbedBuilder
     */
    public EmbedBuilder getTrackingEmbedBuilder(long stageId) {
        return this.trackingEmbedBuilderMap.get(stageId);
    }

    /**
     * Getter for ownMessageId.
     *
     * @return ownMessageId
     */
    public long getOwnMessageId(long stageId) {
        if (this.ownMessageIdMap.containsKey(stageId)) {
            return this.ownMessageIdMap.get(stageId);
        }
        return -1;
    }

    /**
     * Getter for activeMember.
     *
     * @return activeMember
     */
    public Member getActiveMember(long stageId) {
        return this.activeMemberMap.get(stageId);
    }

    public Map<Long, Member> getActiveMemberMap() {
        return this.activeMemberMap;
    }

    /**
     * Updates tracking message timer.
     *
     * @param event GenericGuildVoiceEvent
     */
    private void updateTrackingMessageTimer(GenericGuildEvent event, long stageId) {
        Objects.requireNonNull(event.getGuild().getTextChannelById(Veganizer.STAGE_TRACKING_CHANNEL_ID))
                .retrieveMessageById(this.ownMessageIdMap.get(stageId)).queue(m -> {
                    final int totalSecs = (int) (System.currentTimeMillis() - this.startTimeMap.get(stageId)) / 1000;
                    final int hours = totalSecs / 3600;
                    this.trackingEmbedBuilderMap.get(stageId).setDescription("Time on Stage: "
                            + String.format("%02d:%02d:%02d", hours, (totalSecs % 3600) / 60, totalSecs % 60));
                    if (hours >= 1) {
                        this.trackingEmbedBuilderMap.get(stageId).setColor(Veganizer.YELLOW);
                    }
                    this.updateTrackingMessageEmbed(event, stageId);
                }, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, e -> this.clearVariables(stageId)));
    }

    /**
     * Updates tracking message.
     *
     * @param event GenericGuildVoiceEvent
     */
    private void updateTrackingMessageEmbed(GenericGuildEvent event, long stageId) {
        if (this.ownMessageIdMap.get(stageId) != null) {
            Objects.requireNonNull(event.getGuild().getTextChannelById(Veganizer.STAGE_TRACKING_CHANNEL_ID))
                    .editMessageEmbedsById(this.ownMessageIdMap.get(stageId), this.trackingEmbedBuilderMap.get(stageId).build())
                    .queue(message -> {}, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, e -> {}));
        }
    }

    /**
     * Scales up avatar URL to 256 pixels.
     *
     * @param url Avatar URL
     * @return Avatar URL with size 256 pixels
     */
    private String upscaleAvatar(String url) {
        return url.replace(".png", ".webp?size=256");
    }
}
