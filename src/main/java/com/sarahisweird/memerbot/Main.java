package com.sarahisweird.memerbot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.reaction.Reaction;
import discord4j.core.object.reaction.ReactionEmoji;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Main {
    private static final Snowflake memeArchiveId_release = Snowflake.of("775720910904623135");
    private static final Snowflake memeArchiveId_debug = Snowflake.of("831531360749355078");
    private static final Snowflake updootId = Snowflake.of("826104904057356298");
    private static final Snowflake downdootId = Snowflake.of("826104891843805205");
    private static final List<String> admins = List.of("116927399760756742", "260473563310587904");

    private static Channel memeArchive;

    private static final List<String> memes = new ArrayList<>();

    private static final File memeStore = new File("memes.csv");

    private static boolean messageContainsPicture(Message message) {
        return message.getAttachments().stream().anyMatch(attachment -> attachment.getWidth().isPresent());
    }

    private static Attachment collapseAttachmentSet(Set<Attachment> attachmentSet) {
        return (Attachment) attachmentSet.stream().filter(att -> att.getWidth().isPresent()).toArray()[0];
    }

    public static void main(String[] args) {
        Snowflake memeArchiveId = (args.length > 0) && args[1].equalsIgnoreCase("debug")
                ? memeArchiveId_debug
                : memeArchiveId_release;

        if (memeStore.exists()) {
            try {
                String[] memeIds = Files.readString(memeStore.toPath()).split("\n");

                if (!"".equals(memeIds[0]))
                    memes.addAll(Arrays.asList(memeIds));
            } catch (IOException e) {
                System.err.println("Couldn't read memes to file!");
                e.printStackTrace();
            }
        }

        GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("memerbot_token"))
                .build()
                .gateway()
                .login()
                .block();

        if (client == null) {
            System.err.println("Couldn't create bot!");
            System.exit(1);
        }

        client.getEventDispatcher().on(ReadyEvent.class).subscribe(event -> {
            memeArchive = client.getChannelById(memeArchiveId).block();

            final User self = event.getSelf();

            System.out.println("Logged in as " + self.getUsername() + "#" + self.getDiscriminator() + ".");
        });

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .filter(event -> event.getMessage().getContent().equalsIgnoreCase("#quit"))
                .map(MessageCreateEvent::getMember)
                .map(member -> member.map(Member::getId).map(Snowflake::asString).orElse(""))
                .filter(memberId -> admins.stream().anyMatch(id -> id.equals(memberId)))
                .subscribe(memberId -> client.logout().subscribe());

        client.getEventDispatcher().on(MessageCreateEvent.class)
                .map(MessageCreateEvent::getMessage)
                .filter(Main::messageContainsPicture)
                .map(Message::getId)
                .map(Snowflake::asString)
                .subscribe(memes::add);

        client.getEventDispatcher().on(ReactionAddEvent.class)
                .subscribe(event -> {
                    Message msg = client.getMessageById(event.getChannelId(), event.getMessageId()).block();
                    if (msg == null)
                        return;

                    Optional<User> authorOpt = msg.getAuthor();
                    if (authorOpt.isEmpty())
                        return;

                    User author = authorOpt.get();

                    if (author.getId().equals(client.getSelfId()))
                        return;

                    if (!messageContainsPicture(msg))
                        return;

                    if (memes.stream().noneMatch(id -> id.equals(msg.getId().asString())))
                        return;

                    Attachment attachment = collapseAttachmentSet(msg.getAttachments());

                    int votes = 0;

                    for (Reaction reaction : msg.getReactions()) {
                        Optional<ReactionEmoji.Custom> reactionEmoji = reaction.getEmoji().asCustomEmoji();

                        if (reactionEmoji.isEmpty())
                            continue;

                        Snowflake id = reactionEmoji.get().getId();

                        if (id.equals(updootId))
                            votes += reaction.getCount();

                        if (id.equals(downdootId))
                            votes -= reaction.getCount();
                    }

                    if (votes < 1)
                        return;

                    memeArchive.getRestChannel().createMessage(
                            msg.getAuthor().get().getMention() + "\n" + attachment.getUrl()
                    ).subscribe();

                    memes.remove(msg.getId().asString());
                });

        client.onDisconnect().block();

        StringBuilder memeCSV = new StringBuilder();

        if (!memes.isEmpty()) {
            for (String id: memes) {
                if (memeCSV.length() != 0)
                    memeCSV.append("\n");

                memeCSV.append(id);
            }
        }

        try {
            Files.writeString(memeStore.toPath(), memeCSV.toString());
        } catch (IOException e) {
            System.err.println("Couldn't write memes to file!");
            e.printStackTrace();
        }
    }
}
