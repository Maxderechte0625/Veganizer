package de.steinente.listeners;

import de.steinente.Veganizer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.Objects;

public class ChatListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getGuild().getIdLong() == Veganizer.SERVER_ID) {
            Message message = event.getMessage();
            MessageReference messageReference = message.getMessageReference();
            if (message.getChannel().getType() == ChannelType.TEXT && message.getChannel().asTextChannel().getIdLong() == (Veganizer.STAGE_TRACKING_CHANNEL_ID)
                    && null != messageReference && Objects.requireNonNull(messageReference.getMessage()).getAuthor() == event.getJDA().getSelfUser()
                    && null != messageReference.getMessage().getEmbeds().get(0)) {
                final InteractionListener interactionListener = (InteractionListener) event.getJDA().getRegisteredListeners().get(1);
                final Message botMessage = messageReference.getMessage();
                final MessageEmbed messageEmbed = botMessage.getEmbeds().get(0);
                final EmbedBuilder embedBuilder = new EmbedBuilder(messageEmbed);
                final List<MessageEmbed.Field> fieldList = embedBuilder.getFields();
                final int logIndex = fieldList.indexOf(fieldList.stream().filter(o -> Objects.requireNonNull(o.getName())
                        .equals("Log")).findFirst().orElse(null));
                final String stageName = Objects.requireNonNull(messageReference.getMessage().getEmbeds().get(0).getTitle()).split(" joined ")[1];
                final List<StageChannel> stageChannelList = event.getJDA().getStageChannelsByName(stageName, false);
                final long stageId = stageChannelList.get(stageChannelList.indexOf(stageChannelList.stream().filter(
                        o -> o.getGuild().getIdLong() == Veganizer.SERVER_ID).findFirst().orElse(null))).getIdLong();

                message.delete().queue();
                interactionListener.fixMessageIfBugged(event, messageEmbed, embedBuilder, botMessage, stageId);
                interactionListener.manageSummary(event, botMessage, event.getAuthor().getName(), fieldList,
                        message.getContentDisplay(), embedBuilder, botMessage.getButtons(), logIndex, stageId);
            }
        }
    }
}
