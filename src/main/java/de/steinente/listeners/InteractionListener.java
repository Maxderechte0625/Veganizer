package de.steinente.listeners;

import de.steinente.Veganizer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.internal.interactions.component.TextInputImpl;
import net.dv8tion.jda.internal.interactions.modal.ModalImpl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class InteractionListener extends ListenerAdapter {
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (Objects.requireNonNull(event.getGuild()).getIdLong() == Veganizer.SERVER_ID) {
            final Message message = event.getMessage();
            final MessageEmbed messageEmbed = message.getEmbeds().get(0);
            final EmbedBuilder embedBuilder = new EmbedBuilder(messageEmbed);
            final List<MessageEmbed.Field> fieldList = embedBuilder.getFields();
            final String targetUserId = Objects.requireNonNull(fieldList.get(1).getValue());
            final User targetUser = event.getJDA().retrieveUserById(targetUserId).complete();
            final String targetUserName = null == targetUser ? "" : Objects.requireNonNull(targetUser).getName();
            final Member interactUser = event.getInteraction().getMember();
            assert interactUser != null;
            final String interactUserName = interactUser.getUser().getName();
            final int logIndex = fieldList.indexOf(fieldList.stream().filter(o -> Objects.requireNonNull(o.getName())
                    .equals("Log")).findFirst().orElse(null));
            final List<Button> buttonList = message.getButtons();
            final String stageName = Objects.requireNonNull(message.getEmbeds().get(0).getTitle()).split(" joined ")[1];
            final List<StageChannel> stageChannelList = event.getJDA().getStageChannelsByName(stageName, false);
            final long stageId = stageChannelList.get(stageChannelList.indexOf(stageChannelList.stream().filter(
                    o -> o.getGuild().getIdLong() == Veganizer.SERVER_ID).findFirst().orElse(null))).getIdLong();

            switch (Objects.requireNonNull(event.getButton().getId())) {
                case "summary-button" -> {
                    if (interactUser.getPermissions().contains(Permission.VOICE_MOVE_OTHERS)) {
                        final int summaryIndex = fieldList.indexOf(fieldList.stream().filter(o -> Objects.requireNonNull(o.getName())
                                .startsWith("Summary by")).findFirst().orElse(null));
                        final String summaryMessage = summaryIndex != -1 ? fieldList.get(summaryIndex).getValue() : null;

                        final TextInput summaryMessageInput = new TextInputImpl(
                                "summary-message",
                                TextInputStyle.PARAGRAPH,
                                "Summary message",
                                4,
                                512,
                                true,
                                summaryMessage,
                                "E.g.: Has played soundboard, screamed the N-word, ..."
                        );

                        final Modal summaryModal = new ModalImpl(
                                "summary-modal",
                                "Summary " + targetUserName,
                                List.of(ActionRow.of(summaryMessageInput))
                        );

                        event.replyModal(summaryModal).queue();
                    } else {
                        event.reply(Veganizer.NO_PERMISSIONS).setEphemeral(true).queue();
                    }
                }
                case "talk-button" -> {
                    if (null != targetUser) {
                        final Member targetMember = Objects.requireNonNull(event.getGuild()).getMember(targetUser);
                        if (null != targetMember) {
                            final Role talkRole = event.getJDA().getRoleById(Veganizer.TALK_ROLE_ID);
                            assert talkRole != null;
                            if (interactUser.hasPermission(Permission.MANAGE_ROLES) && interactUser.canInteract(talkRole)) {
                                if (!targetMember.getRoles().contains(talkRole)) {
                                    Objects.requireNonNull(event.getGuild()).addRoleToMember(targetUser, talkRole).queue();
                                    message.editMessageEmbeds(messageEmbed).setActionRow(buttonList.get(0),
                                            buttonList.get(1).withLabel("Remove Talk"), buttonList.get(2), buttonList.get(3)).queue();
                                    this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Added Talk", stageId);
                                    event.deferEdit().queue();
                                } else {
                                    Objects.requireNonNull(event.getGuild()).removeRoleFromMember(targetUser, talkRole).queue();
                                    message.editMessageEmbeds(messageEmbed).setActionRow(buttonList.get(0),
                                            buttonList.get(1).withLabel("Add Talk"), buttonList.get(2), buttonList.get(3)).queue();
                                    this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Removed Talk", stageId);
                                    event.deferEdit().queue();
                                }
                            } else {
                                event.reply(Veganizer.NO_PERMISSIONS).setEphemeral(true).queue();
                            }
                        } else {
                            event.reply(Veganizer.NOT_ON_SERVER).setEphemeral(true).queue();
                        }
                    } else {
                        event.reply(Veganizer.NOT_ON_SERVER).setEphemeral(true).queue();
                    }
                }
                case "void-button" -> {
                    if (null != targetUser) {
                        final Member targetMember = Objects.requireNonNull(event.getGuild()).getMember(targetUser);
                        if (null != targetMember) {
                            final Role voidRole = event.getJDA().getRoleById(Veganizer.VOID_ROLE_ID);
                            assert voidRole != null;
                            if (interactUser.hasPermission(Permission.MANAGE_ROLES) && interactUser.canInteract(voidRole)) {
                                if (!targetMember.getRoles().contains(voidRole)) {
                                    Objects.requireNonNull(event.getGuild()).addRoleToMember(targetUser, voidRole).queue();
                                    message.editMessageEmbeds(messageEmbed).setActionRow(buttonList.get(0),
                                            buttonList.get(1), buttonList.get(2).withLabel("Remove Void"), buttonList.get(3)).queue();
                                    this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Added Void", stageId);
                                    event.deferEdit().queue();
                                } else {
                                    Objects.requireNonNull(event.getGuild()).removeRoleFromMember(targetUser, voidRole).queue();
                                    message.editMessageEmbeds(messageEmbed).setActionRow(buttonList.get(0),
                                            buttonList.get(1), buttonList.get(2).withLabel("Add Void"), buttonList.get(3)).queue();
                                    this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Removed Void", stageId);
                                    event.deferEdit().queue();
                                }
                            } else {
                                event.reply(Veganizer.NO_PERMISSIONS).setEphemeral(true).queue();
                            }
                        } else {
                            event.reply(Veganizer.NOT_ON_SERVER).setEphemeral(true).queue();
                        }
                    } else {
                        event.reply(Veganizer.NOT_ON_SERVER).setEphemeral(true).queue();
                    }
                }
                case "ban-button" -> {
                    if (interactUser.hasPermission(Permission.BAN_MEMBERS)) {
                        if (null != targetUser) {
                            final TextInput banReasonInput = new TextInputImpl(
                                    "ban-reason",
                                    TextInputStyle.PARAGRAPH,
                                    "Ban reason",
                                    4,
                                    512,
                                    false,
                                    null,
                                    "E.g.: Has offended, made racist remarks, ..."
                            );

                            final Modal banModal = new ModalImpl(
                                    "ban-modal",
                                    "Ban " + targetUserName,
                                    List.of(ActionRow.of(banReasonInput))
                            );

                            event.replyModal(banModal).queue();
                        } else {
                            event.reply(Veganizer.NOT_ON_SERVER).setEphemeral(true).queue();
                        }
                    } else {
                        event.reply(Veganizer.NO_PERMISSIONS).setEphemeral(true).queue();
                    }
                }
                case "unban-button" -> {
                    if (interactUser.hasPermission(Permission.BAN_MEMBERS)) {
                        Objects.requireNonNull(event.getGuild()).unban(UserSnowflake.fromId(targetUserId)).queue();
                        message.editMessageEmbeds(messageEmbed).setActionRow(buttonList.get(0), buttonList.get(1), buttonList.get(2),
                                buttonList.get(3).withLabel("Ban").withId("ban-button")).queue();
                        this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Unbanned", stageId);
                        event.deferEdit().queue();
                    } else {
                        event.reply(Veganizer.NO_PERMISSIONS).setEphemeral(true).queue();
                    }
                }
            }

            this.fixMessageIfBugged(event, messageEmbed, embedBuilder, message, stageId);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (Objects.requireNonNull(event.getGuild()).getIdLong() == Veganizer.SERVER_ID) {
            final Message message = event.getMessage();
            assert message != null;
            final MessageEmbed messageEmbed = message.getEmbeds().get(0);
            final EmbedBuilder embedBuilder = new EmbedBuilder(messageEmbed);
            final List<MessageEmbed.Field> fieldList = embedBuilder.getFields();
            final List<Button> buttonList = message.getButtons();
            final User targetUser = event.getJDA().retrieveUserById(Objects.requireNonNull(messageEmbed.getFields().get(1).getValue())).complete();
            assert targetUser != null;
            final String interactUserName = event.getInteraction().getUser().getName();
            final int logIndex = fieldList.indexOf(fieldList.stream().filter(o -> Objects.requireNonNull(o.getName())
                    .equals("Log")).findFirst().orElse(null));
            final String stageName = Objects.requireNonNull(message.getEmbeds().get(0).getTitle()).split(" joined ")[1];
            final List<StageChannel> stageChannelList = event.getJDA().getStageChannelsByName(stageName, false);
            final long stageId = stageChannelList.get(stageChannelList.indexOf(stageChannelList.stream().filter(
                    o -> o.getGuild().getIdLong() == Veganizer.SERVER_ID).findFirst().orElse(null))).getIdLong();

            switch (event.getInteraction().getModalId()) {
                case "summary-modal" -> {
                    this.manageSummary(event, message, interactUserName, fieldList, Objects.requireNonNull(event.getValue("summary-message"))
                            .getAsString(), embedBuilder, buttonList, logIndex, stageId);
                    event.deferEdit().queue();
                }
                case "ban-modal" -> {
                    Objects.requireNonNull(event.getGuild()).ban(targetUser, 0, TimeUnit.SECONDS)
                            .reason(Objects.requireNonNull(event.getValue("ban-reason")).getAsString())
                            .queue();
                    message.editMessageEmbeds(messageEmbed).setActionRow(buttonList.get(0), buttonList.get(1), buttonList.get(2),
                            buttonList.get(3).withLabel("Unban").withId("unban-button")).queue();
                    this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Banned", stageId);
                    event.deferEdit().queue();
                }
            }
        }
    }

    /**
     * Manages the summary field.
     *
     * @param event            event
     * @param message          message
     * @param interactUserName username of interacting user
     * @param fieldList        list of fields of embed
     * @param summary          summary
     * @param embedBuilder     embed builder
     * @param buttonList       list of buttons
     * @param logIndex         index of log field
     * @param stageId          ID of stage
     */
    public void manageSummary(Event event, Message message, String interactUserName, List<MessageEmbed.Field> fieldList,
                              String summary, EmbedBuilder embedBuilder, List<Button> buttonList, int logIndex, long stageId) {
        final VoiceListener voiceListener = (VoiceListener) event.getJDA().getRegisteredListeners().get(0);
        final boolean isUserOnStage = message.getIdLong() == voiceListener.getOwnMessageId(stageId);
        final MessageEmbed.Field summaryField = new MessageEmbed.Field("Summary by " + interactUserName, summary, false);
        final int summaryIndex = fieldList.indexOf(fieldList.stream().filter(o -> Objects.requireNonNull(o.getName())
                .startsWith("Summary by")).findFirst().orElse(null));

        // If summary do NOT exist
        if (summaryIndex == -1) {
            embedBuilder.addField(summaryField);
            message.editMessageEmbeds(embedBuilder.build()).setActionRow(Objects.requireNonNull(buttonList.get(0))
                    .withLabel("Edit Summary"), buttonList.get(1), buttonList.get(2), buttonList.get(3)).queue();
            if (isUserOnStage) voiceListener.getTrackingEmbedBuilder(stageId).addField(summaryField);
            this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Added Summary", stageId);
        } else {
            fieldList.set(summaryIndex, summaryField);
            message.editMessageEmbeds(embedBuilder.build()).queue();
            if (isUserOnStage)
                voiceListener.getTrackingEmbedBuilder(stageId).getFields().set(summaryIndex, summaryField);
            this.appendLog(event, logIndex, interactUserName, fieldList, message, embedBuilder, "Edited Summary", stageId);
        }
    }

    /**
     * Fixes the message if the bot restarts while user is on the stage.
     *
     * @param event        event
     * @param messageEmbed embed of message
     * @param embedBuilder embed builder of message
     * @param message      message
     * @param stageId      ID of stage
     */
    public void fixMessageIfBugged(Event event, MessageEmbed messageEmbed, EmbedBuilder embedBuilder, Message message, long stageId) {
        final VoiceListener voiceListener = (VoiceListener) event.getJDA().getRegisteredListeners().get(0);
        if ((Objects.equals(messageEmbed.getColor(), Veganizer.GREEN) || Objects.equals(messageEmbed.getColor(), Veganizer.YELLOW))
                && null == voiceListener.getActiveMember(stageId)) {
            embedBuilder.setColor(Veganizer.RED);
            embedBuilder.setDescription(messageEmbed.getDescription() + " (Maybe incorrect)");
            message.editMessageEmbeds(embedBuilder.build()).queue();
        }
    }

    /**
     * Appends a log to message embed.
     *
     * @param event            event
     * @param logIndex         index of log field
     * @param interactUserName interacting username
     * @param fieldList        list of fields
     * @param message          target message
     * @param embedBuilder     target message embed builder
     * @param logBody          log body message
     * @param stageId          ID of stage
     */
    private void appendLog(Event event, int logIndex, String interactUserName, List<MessageEmbed.Field> fieldList,
                           Message message, EmbedBuilder embedBuilder, String logBody, long stageId) {
        final VoiceListener voiceListener = (VoiceListener) event.getJDA().getRegisteredListeners().get(0);
        final MessageEmbed.Field oldLogField = logIndex == -1 ? null : fieldList.get(logIndex);
        final String log = logBody + " by " + interactUserName
                + " [" + DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").format(LocalDateTime.now()) + "]";
        final boolean isUserOnStage = message.getIdLong() == voiceListener.getOwnMessageId(stageId);

        if (null == oldLogField) {
            final MessageEmbed.Field newLogField = new MessageEmbed.Field(
                    "Log",
                    log,
                    false
            );
            fieldList.add(newLogField);
            if (isUserOnStage) voiceListener.getTrackingEmbedBuilder(stageId).addField(newLogField);
        } else {
            final MessageEmbed.Field newLogField = new MessageEmbed.Field(
                    "Log",
                    oldLogField.getValue() + "\n" + log,
                    false
            );
            fieldList.set(logIndex, newLogField);
            if (isUserOnStage) voiceListener.getTrackingEmbedBuilder(stageId).getFields().set(logIndex, newLogField);
        }
        message.editMessageEmbeds(embedBuilder.build()).queue();
    }
}