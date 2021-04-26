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
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.MessageCreateSpec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class Main {
    private static final Snowflake memeArchiveId_release = Snowflake.of("775720910904623135");
    private static final Snowflake memeArchiveId_debug = Snowflake.of("831531360749355078");
    private static final Snowflake updootId = Snowflake.of("826104904057356298");
    private static final ReactionEmoji updootReaction = ReactionEmoji.custom(updootId, "updoot", false);
    private static final Snowflake downdootId = Snowflake.of("826104891843805205");
    private static final ReactionEmoji downdootReaction = ReactionEmoji.custom(downdootId, "downdoot", false);
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
        boolean isDebug = (args.length > 0) && args[0].equalsIgnoreCase("debug");
        Snowflake memeArchiveId = isDebug
                ? memeArchiveId_debug
                : memeArchiveId_release;

        if (isDebug) {
            System.out.println("Running in debug mode!");
        }

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

                    votes += msg.getReactors(updootReaction)
                            .filter(user -> isDebug || !user.equals(author))
                            .count()
                            .blockOptional()
                            .orElse(0L);

                    votes -= msg.getReactors(downdootReaction)
                            .filter(user -> isDebug || !user.equals(author))
                            .count()
                            .blockOptional()
                            .orElse(0L);

                    if (votes < (isDebug ? 1 : 2))
                        return;

                    URL fileUrl;
                    InputStream fileInputStream;

                    try {
                        fileUrl = new URL(attachment.getUrl());
                        fileInputStream = fileUrl.openStream();
                    } catch (MalformedURLException e) {
                        System.err.println("Discord didn't give us a correct URL?");
                        e.printStackTrace();
                        return;
                    } catch (IOException e) {
                        System.err.println("Couldn't open stream to attachment!");
                        e.printStackTrace();
                        return;
                    }

                    MessageCreateSpec messageCreateSpec = new MessageCreateSpec();
                    messageCreateSpec.setContent(author.getMention());
                    messageCreateSpec.addFile(attachment.getFilename(), fileInputStream);

                    memeArchive.getRestChannel()
                            .createMessage(messageCreateSpec.asRequest())
                            .subscribe(msgData -> {
                                try {
                                    fileInputStream.close();
                                } catch (IOException e) {
                                    System.err.println("Couldn't close attachment stream!");
                                    e.printStackTrace();
                                }
                            });

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
