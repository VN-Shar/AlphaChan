package AlphaChan.main.handler;

import arc.files.*;
import arc.util.io.Streams;

import mindustry.*;
import mindustry.game.*;
import mindustry.type.ItemStack;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.message.*;
import net.dv8tion.jda.api.hooks.*;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import javax.annotation.Nonnull;
import javax.imageio.*;

import org.bson.Document;

import AlphaChan.main.data.user.GuildData;
import AlphaChan.main.data.user.UserData;
import AlphaChan.main.data.user.GuildData.CHANNEL_TYPE;
import AlphaChan.main.handler.DatabaseHandler.LOG_TYPE;
import AlphaChan.main.util.Log;

import java.awt.image.*;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static AlphaChan.AlphaChan.jda;

public final class MessageHandler extends ListenerAdapter {

    private static MessageHandler instance = new MessageHandler();

    public final Integer messageAliveTime = 30;

    public HashMap<String, TextChannel> serverChatChannel = new HashMap<String, TextChannel>();

    private MessageHandler() {

        jda.addEventListener(this);
        Log.system("Message handler up");
    }

    public static MessageHandler getInstance() {
        if (instance == null)
            instance = new MessageHandler();
        return instance;
    }

    private static String getMessageSender(Guild guild, Category category, Channel channel, Member member) {
        String guildName = guild == null ? "Unknown" : guild.getName();
        String categoryName = category == null ? "Unknown" : category.getName();
        String channelName = channel == null ? "Unknown" : channel.getName();
        String memberName = member == null ? "Unknown" : member.getEffectiveName();

        return "[" + guildName + "] " + "<" + categoryName + ":" + channelName + "> " + memberName;

    }

    public static String getMessageSender(Message message) {
        return getMessageSender(message.getGuild(), message.getCategory(), message.getChannel(), message.getMember());
    }

    public static String getMessageSender(@Nonnull SlashCommandInteractionEvent event) {
        return getMessageSender(event.getGuild(), null, event.getChannel(), event.getMember());
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        Message message = event.getMessage();
        if (message.getAuthor().isBot())
            return;
        // Process the message
        handleMessage(message);
    }

    public static void handleMessage(Message message) {
        // Log all message that has been sent
        List<Attachment> attachments = message.getAttachments();
        Member member = message.getMember();

        // Schematic preview
        if ((ContentHandler.isSchematicText(message) && attachments.isEmpty()) || ContentHandler
                .isSchematicFile(attachments)) {
            Log.system(getMessageSender(message) + ": sent a schematic ");
            sendSchematicPreview(message);
        }

        else if (ContentHandler.isMapFile(attachments)) {
            sendMapPreview(message, message.getChannel());
        }

        // Delete in channel that it should not be
        GuildData guildData = GuildHandler.getGuild(message.getGuild());

        guildData.resetTimer();
        if (guildData._containsChannel(CHANNEL_TYPE.SCHEMATIC.name(), message.getTextChannel().getId()) || //
                guildData._containsChannel(CHANNEL_TYPE.MAP.name(), message.getTextChannel().getId())) {
            if (!message.getContentRaw().isEmpty()) {
                message.delete().queue();
                replyMessage(message, "Vui lòng không gửi tin nhắn vào kênh này!", 30);
            }
        }

        // Update exp on message sent
        UserHandler.onMessage(message);
        DatabaseHandler.log(LOG_TYPE.MESSAGE, new Document()//
                .append("message", getMessageSender(message) + ": " + message.getContentDisplay())//
                .append("messageId", message.getId())//
                .append("userId", member == null ? null : member.getId())//
                .append("guildId", message.getGuild().getId()));

        // Log member message/file/image url to terminals
        if (!message.getContentRaw().isEmpty())
            Log.print("LOG", getMessageSender(message) + ": " + message.getContentDisplay());

        else if (!message.getAttachments().isEmpty())
            message.getAttachments().forEach(attachment -> {
                Log.print("LOG", getMessageSender(message) + ": " + attachment.getUrl());
            });

    }

    @Override
    public void onGuildMemberUpdateNickname(@Nonnull GuildMemberUpdateNicknameEvent event) {
        Member member = event.getMember();
        Member bot = event.getGuild().getMember(jda.getSelfUser());
        if (member == bot)
            return;
        Member target = event.getEntity();
        UserData userData = UserHandler.getUserNoCache(target);
        if (userData == null)
            throw new IllegalStateException("No user data found");

        if (bot == null)
            throw new IllegalStateException("Bot not in guild");
        userData._displayLevelName();

    }

    @Override
    public void onMessageDelete(@Nonnull MessageDeleteEvent event) {
        DatabaseHandler.log(LOG_TYPE.MESSAGE_DELETED, new Document("messageId", event.getMessageId()));
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        // Send invite link to member who left the guild
        User user = event.getUser();
        List<TextChannel> inviteChannels = event.getGuild().getTextChannels();
        if (!inviteChannels.isEmpty()) {
            Invite invite = inviteChannels.get(0).createInvite().complete();
            user.openPrivateChannel().queue(channel -> channel.sendMessage(invite.getUrl()).queue());
        }
        log(event.getGuild(), user.getName() + " rời máy chủ");
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        UserHandler.addUser(event.getMember());
        log(event.getGuild(), event.getMember().getEffectiveName() + " tham gia máy chủ");
    }

    public static void log(Guild guild, @Nonnull String content) {
        GuildData guildData = GuildHandler.getGuild(guild);
        if (guildData == null)
            throw new IllegalStateException("No guild data found");

        List<TextChannel> botLogChannel = guildData._getChannels(CHANNEL_TYPE.BOT_LOG.name());
        if (botLogChannel == null) {
            Log.error("Bot log channel for guild <" + guild.getName() + "> does not exists");
        } else
            botLogChannel.forEach(c -> c.sendMessage("```" + content + "```").queue());
    }

    public static boolean isChannel(Guild guild, Channel channel,
            HashMap<String, HashMap<String, String>> guildChannelIds) {
        if (guildChannelIds.containsKey(guild.getId())) {
            if (guildChannelIds.get(guild.getId()).containsKey(channel.getId()))
                return true;
        }
        return false;
    }

    public static boolean hasChannel(@Nonnull String guildId, @Nonnull String channelId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null)
            return false;
        List<GuildChannel> channel = guild.getChannels();
        for (GuildChannel c : channel) {
            if (c.getId().equals(channelId))
                return true;
        }
        return false;
    }

    public static void sendMapPreview(Attachment attachment, Member member, MessageChannel channel) {
        try {

            ContentHandler.Map map = ContentHandler.readMap(NetworkHandler.download(attachment.getUrl()));
            new File("cache/").mkdir();
            new File("cache/temp/").mkdir();
            File mapFile = new File("cache/temp/" + attachment.getFileName());
            Fi imageFile = Fi.get("cache/temp/image_" + attachment.getFileName().replace(".msav", ".png"));
            Streams.copy(NetworkHandler.download(attachment.getUrl()), new FileOutputStream(mapFile));
            ImageIO.write(map.image, "png", imageFile.file());

            EmbedBuilder builder = new EmbedBuilder().setImage("attachment://" + imageFile.name())
                    .setAuthor(member.getEffectiveName(), member.getEffectiveAvatarUrl(),
                            member.getEffectiveAvatarUrl())
                    .setTitle(map.name == null ? attachment.getFileName().replace(".msav", "") : map.name);
            builder.addField("Size: ", map.image.getWidth() + "x" + map.image.getHeight(), false);
            if (map.description != null)
                builder.setFooter(map.description);

            File f = imageFile.file();
            if (f == null)
                return;
            channel.sendFile(mapFile).addFile(f).setEmbeds(builder.build()).queue();

        } catch (Exception e) {
            Log.error(e);
        }
    }

    public static void sendMapPreview(Message message, MessageChannel channel) {
        for (int i = 0; i < message.getAttachments().size(); i++) {
            Attachment attachment = message.getAttachments().get(i);
            if (ContentHandler.isMapFile(attachment)) {
                sendMapPreview(attachment, message.getMember(), channel);
                message.delete().queue();
            }
        }
    }

    public static void sendMapPreview(SlashCommandInteractionEvent event) {
        OptionMapping fileOption = event.getOption("mapfile");
        if (fileOption == null)
            return;
        Attachment attachment = fileOption.getAsAttachment();

        if (!ContentHandler.isMapFile(attachment)) {
            event.reply("File được chọn không phải là file bản đồ");
            return;
        }

        Member member = event.getMember();
        sendMapPreview(attachment, member, event.getChannel());
        event.reply("Gửi thành công.");
    }

    public static void sendSchematicPreview(SlashCommandInteractionEvent event) {
        OptionMapping fileOption = event.getOption("schematicfile");
        if (fileOption == null)
            return;
        Attachment attachment = fileOption.getAsAttachment();

        if (!ContentHandler.isSchematicFile(attachment)) {
            event.reply("File được chọn không phải là file bản thiết kế");
            return;
        }
        Member member = event.getMember();
        try {
            sendSchematicPreview(ContentHandler.parseSchematicURL(attachment.getUrl()), member, event.getChannel());
        } catch (Exception e) {
            Log.error(e);
        }
    }

    public static void sendSchematicPreview(Message message) {
        try {
            if (ContentHandler.isSchematicText(message)) {
                sendSchematicPreview(ContentHandler.parseSchematic(message.getContentRaw()), message);
            } else {
                for (int i = 0; i < message.getAttachments().size(); i++) {
                    Attachment attachment = message.getAttachments().get(i);

                    if (ContentHandler.isSchematicFile(attachment)) {
                        sendSchematicPreview(ContentHandler.parseSchematicURL(attachment.getUrl()), message);
                    }
                }
            }
        } catch (Exception e) {
            Log.error(e);
        }
        message.delete().queue();
    }

    public static void sendSchematicPreview(Schematic schem, Message message) {
        sendSchematicPreview(schem, message.getMember(), message.getChannel());
    }

    public static void sendSchematicPreview(Schematic schem, Member member, MessageChannel channel) {
        try {
            File schemFile = getSchematicFile(schem);
            File previewFile = getSchematicPreviewFile(schem);
            EmbedBuilder builder = getSchematicEmbedBuilder(schem, previewFile, member);

            channel.sendFile(schemFile).addFile(previewFile).setEmbeds(builder.build()).queue();
        } catch (Exception e) {
            sendMessage(channel, "Lỗi: " + e.getMessage(), 30);
        }
    }

    public static EmbedBuilder getSchematicEmbedBuilder(Schematic schem, File previewFile, Member member) {
        EmbedBuilder builder = new EmbedBuilder().setImage("attachment://" + previewFile.getName())
                .setAuthor(member.getEffectiveName(), member.getEffectiveAvatarUrl(), member.getEffectiveAvatarUrl())
                .setTitle(schem.name());

        if (!schem.description().isEmpty())
            builder.setFooter(schem.description());

        // Schem heigh, width
        builder.addField("Kích thước", "Rộng: " + String.valueOf(schem.width) + " Cao: " + String.valueOf(schem.height),
                true);

        StringBuilder requirement = new StringBuilder();

        // Item requirements
        for (ItemStack stack : schem.requirements()) {
            List<Emote> emotes = member.getGuild().getEmotesByName(stack.item.name.replace("-", ""), true);

            if (!emotes.isEmpty())
                requirement.append(emotes.get(0).getAsMention()).append(stack.amount).append("  ");
            else
                requirement.append(stack.item.name + ": " + stack.amount + " ");
        }

        builder.addField("Tài nguyên cần", requirement.toString(), false);

        // Power input/output

        requirement = new StringBuilder();

        int powerProduction = (int) Math.round(schem.powerProduction()) * 60;
        int powerConsumption = (int) Math.round(schem.powerConsumption()) * 60;

        if (powerConsumption != 0)
            requirement.append("\nNăng lượng sử dụng: " + String.valueOf(powerConsumption) + "/s");

        if (powerProduction != 0)
            requirement.append("\nNăng lượng tạo ra: " + String.valueOf(powerProduction) + "/s");

        builder.addField("Năng lượng", requirement.toString(), false);

        HashMap<String, Float> input = ContentHandler.getSchematicInput(schem);
        HashMap<String, Float> output = ContentHandler.getSchematicOutput(schem);

        StringBuilder inputString = new StringBuilder();

        for (String key : input.keySet()) {

            List<Emote> emotes = member.getGuild().getEmotesByName(key.replace("-", ""), true);

            inputString.append(emotes.isEmpty() ? key + ": " : emotes.get(0).getAsMention());
            inputString.append(" " + input.get(key) + "/s ");
        }

        StringBuilder outputString = new StringBuilder();

        for (String key : output.keySet()) {

            List<Emote> emotes = member.getGuild().getEmotesByName(key.replace("-", ""), true);

            outputString.append(emotes.isEmpty() ? key + ": " : emotes.get(0).getAsMention());
            outputString.append(" " + output.get(key) + "/s ");
        }

        builder.addField("Tổng đầu vào", inputString.toString(), false);
        builder.addField("Tổng đầu ra", outputString.toString(), false);

        return builder;

    }

    public static @Nonnull File getSchematicFile(Schematic schem) throws IOException {
        String sname = schem.name().replace("/", "_").replace(" ", "_").replace(":", "_");
        new File("cache").mkdir();
        if (sname.isEmpty())
            sname = "empty";
        File schemFile = new File("cache/temp/" + sname + "." + Vars.schematicExtension);
        Schematics.write(schem, new Fi(schemFile));
        return schemFile;
    }

    public static @Nonnull File getSchematicPreviewFile(Schematic schem) throws Exception {

        BufferedImage preview = ContentHandler.previewSchematic(schem);
        new File("cache").mkdir();
        File previewFile = new File("cache/temp/img_" + UUID.randomUUID() + ".png");
        ImageIO.write(preview, "png", previewFile);

        return previewFile;

    }

    // Message send commands
    public static void replyMessage(SlashCommandInteractionEvent event, String content, int deleteAfter) {
        sendMessage(event.getChannel(), content, deleteAfter);
    }

    public static void sendMessage(MessageChannel channel, String content, int deleteAfter) {
        if (channel != null)
            channel.sendMessage("```" + content + "```")
                    .queue(m -> m.delete().queueAfter(deleteAfter, TimeUnit.SECONDS));
    }

    public static void replyMessage(Message message, String content, int deleteAfter) {
        message.reply("```" + content + "```").queue(m -> m.delete().queueAfter(deleteAfter, TimeUnit.SECONDS));
    }

    public static void replyMessage(Message message, String content) {
        message.reply("```" + content + "```").queue();
    }

    public static void sendEmbed(SlashCommandInteractionEvent event, EmbedBuilder builder, int deleteAfter) {
        event.getHook().sendMessageEmbeds(builder.build())
                .queue(_message -> _message.delete().queueAfter(deleteAfter, TimeUnit.SECONDS));
    }

    public static void sendEmbed(MessageContextInteractionEvent event, EmbedBuilder builder, int deleteAfter) {
        event.getHook().sendMessageEmbeds(builder.build())
                .queue(_message -> _message.delete().queueAfter(deleteAfter, TimeUnit.SECONDS));
    }

    public static void sendMessage(MessageContextInteractionEvent event, String content, int deleteAfter) {
        event.getHook().sendMessage("```" + content + "```")
                .queue(_message -> _message.delete().queueAfter(deleteAfter, TimeUnit.SECONDS));
    }
}
