package com.sarahisweird.memerbot;

import com.sarahisweird.memerbot.tracking.MemeStore;
import com.sarahisweird.memerbot.tracking.TrackingState;
import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.discordjson.json.MessageCreateRequest;
import discord4j.discordjson.json.MessageEditRequest;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;

public class EventHandler extends ReactiveEventAdapter {
    private final Config config;

    public EventHandler() {
        this.config = Config.getInstance();
    }

    @Override
    public Publisher<?> onReady(ReadyEvent event) {
        return Mono.fromRunnable(() -> {
            final User self = event.getSelf();

            System.out.println("Logged in as " + self.getUsername() + "#" + self.getDiscriminator() + ".");
        });
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event) {
        MemeStore.getInstance().yeetOldOnes();

        if (event.getMessage().getContent().equalsIgnoreCase(config.getPrefix() + "quit")) {
            Optional<Member> member = event.getMember();

            if (member.isEmpty())
                return Mono.empty();

            if (!config.getAdmins().contains(member.get().getId().asString()))
                return Mono.empty();

            return Mono.fromRunnable(() -> event.getClient().logout().subscribe());
        }

        if (Util.messageContainsPicture(event.getMessage())) {
            MemeStore.getInstance().trackMeme(event.getMessage().getId());
        }

        return Mono.just(event.getMessage().getId());
    }

    private Long countUpvotes(Message msg) {
        return msg.getReactors(config.getUpvoteEmote())
                .filter(user -> config.isDebug() || !user.equals(msg.getAuthor().orElse(null)))
                .count()
                .blockOptional()
                .orElse(0L);
    }

    private Long countDownvotes(Message msg) {
        return msg.getReactors(config.getDownvoteEmote())
                .filter(user -> config.isDebug() || !user.equals(msg.getAuthor().orElse(null)))
                .count()
                .blockOptional()
                .orElse(0L);
    }

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event) {
        MemeStore memeStore = MemeStore.getInstance();
        return event.getMessage().map(msg -> {
            Optional<User> author = msg.getAuthor();

            if (author.isEmpty()) // Empty author, for whatever reason
                return Mono.empty();

            if (author.get().getId().equals(event.getClient().getSelfId())) // Don't wanna send messages by us
                return Mono.empty();

            if (!memeStore.isTracked(msg.getId())) // Not tracked? Don't care
                return Mono.empty();

            TrackingState state = memeStore.getTrackingState(msg.getId());
            Long upvotes = countUpvotes(msg);
            Long downvotes = countDownvotes(msg);

            if (upvotes - downvotes < config.getRequiredVotes() && state == TrackingState.PENDING_ARCHIVING)
                return Mono.empty();

            // Pending
            if (state == TrackingState.PENDING_ARCHIVING) {
                config.getMemeArchive().getRestChannel().createMessage(makeEmbed(msg, upvotes, downvotes))
                        .subscribe(msgData -> memeStore.promote(msg.getId(), Snowflake.of(msgData.id())));

                return Mono.empty();
            }

            // Update votes
            Snowflake archiveId = memeStore.getArchivedId(msg.getId());

            config.getMemeArchive().getRestChannel().getRestMessage(archiveId).edit(
                    MessageEditRequest.builder().embed(makeEmbed(msg, upvotes, downvotes)).build()
            ).subscribe();

            return Mono.empty();
        });
    }

    private EmbedData makeEmbed(Message msg, Long upvotes, Long downvotes) {
        Optional<Member> author = msg.getAuthorAsMember().blockOptional();

        if (author.isEmpty())
            return null;

        Attachment attachment = Util.collapseAttachmentSet(msg.getAttachments());

        EmbedCreateSpec embedCreateSpec = new EmbedCreateSpec();
        embedCreateSpec.setTitle(config.getRandomTitle(msg));
        embedCreateSpec.setAuthor(
                author.get().getUsername() + "#" + author.get().getDiscriminator(),
                null,
                author.get().getAvatarUrl()
        );
        embedCreateSpec.setImage(attachment.getUrl());
        embedCreateSpec.addField(config.getUpvoteText(), upvotes.toString(), true);
        embedCreateSpec.addField(config.getDownvoteText(), downvotes.toString(), true);

        return embedCreateSpec.asRequest();
    }
}
