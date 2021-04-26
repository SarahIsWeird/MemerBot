package com.sarahisweird.memerbot;

import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.spec.MessageCreateSpec;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

public class EventHandler extends ReactiveEventAdapter {
    @Override
    public Publisher<?> onReady(ReadyEvent event) {
        return Mono.fromRunnable(() -> {
            Snowflake memeArchiveId = Config.isDebug
                    ? Config.memeArchiveId_debug
                    : Config.memeArchiveId_release;

            event.getClient().getChannelById(memeArchiveId).subscribe(channel -> Config.memeArchive = channel);

            final User self = event.getSelf();

            System.out.println("Logged in as " + self.getUsername() + "#" + self.getDiscriminator() + ".");
        });
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event) {
        if (event.getMessage().getContent().equalsIgnoreCase("#quit")) {
            Optional<Member> member = event.getMember();

            if (member.isEmpty())
                return Mono.empty();

            if (!Config.admins.contains(member.get().getId().asString()))
                return Mono.empty();

            return Mono.fromRunnable(() -> event.getClient().logout().subscribe());
        }

        if (Util.messageContainsPicture(event.getMessage())) {
            MemeStore.getInstance().trackMeme(event.getMessage().getId());
        }

        return Mono.empty();
    }

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event) {
        return event.getMessage().map(msg -> {
            Optional<User> author = msg.getAuthor();

            if (author.isEmpty())
                return Mono.empty();

            if (author.get().getId().equals(event.getClient().getSelfId()))
                return Mono.empty();

            if (!MemeStore.getInstance().isTracked(msg.getId()))
                return Mono.empty();

            Attachment attachment = Util.collapseAttachmentSet(msg.getAttachments());

            int votes = 0;

            votes += msg.getReactors(Config.updootReaction)
                    .filter(user -> Config.isDebug || !user.equals(author.get()))
                    .count()
                    .blockOptional()
                    .orElse(0L);

            votes -= msg.getReactors(Config.downdootReaction)
                    .filter(user -> Config.isDebug || !user.equals(author.get()))
                    .count()
                    .blockOptional()
                    .orElse(0L);

            if (votes < (Config.isDebug ? 1 : 2))
                return Mono.empty();

            URL fileUrl;
            InputStream fileInputStream;

            try {
                fileUrl = new URL(attachment.getUrl());
                fileInputStream = fileUrl.openStream();
            } catch (MalformedURLException e) {
                System.err.println("Discord didn't give us a correct URL?");
                e.printStackTrace();
                return Mono.empty();
            } catch (IOException e) {
                System.err.println("Couldn't open stream to attachment!");
                e.printStackTrace();
                return Mono.empty();
            }

            MessageCreateSpec messageCreateSpec = new MessageCreateSpec();
            messageCreateSpec.setContent(author.get().getMention());
            messageCreateSpec.addFile(attachment.getFilename(), fileInputStream);

            Config.memeArchive.getRestChannel()
                    .createMessage(messageCreateSpec.asRequest())
                    .subscribe(msgData -> {
                        try {
                            fileInputStream.close();
                        } catch (IOException e) {
                            System.err.println("Couldn't close attachment stream!");
                            e.printStackTrace();
                        }
                    });

            MemeStore.getInstance().removeTracking(msg.getId());

            return Mono.empty();
        });
    }
}
