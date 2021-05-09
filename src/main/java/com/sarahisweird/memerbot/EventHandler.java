package com.sarahisweird.memerbot;

import com.sarahisweird.memerbot.economy.EcoDB;
import com.sarahisweird.memerbot.tracking.MemeStore;
import com.sarahisweird.memerbot.tracking.OwoCounter;
import com.sarahisweird.memerbot.tracking.TrackingState;
import com.sarahisweird.memerbot.tracking.UwuCounter;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.event.domain.message.ReactionRemoveEvent;
import discord4j.core.object.entity.*;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.discordjson.json.MessageEditRequest;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.util.Color;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

public class EventHandler extends ReactiveEventAdapter {
    private final Config config;
    private final EcoDB ecoDB;
    private final UwuCounter uwuCounter;
    private final OwoCounter owoCounter;
    private final Random random;

    public EventHandler() {
        this.config = Config.getInstance();
        this.ecoDB = EcoDB.getInstance();
        this.uwuCounter = UwuCounter.getInstance();
        this.owoCounter = OwoCounter.getInstance();
        this.random = new Random();
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

        Message msg = event.getMessage();

        if (msg.getGuildId().isEmpty()) return Mono.empty();
        if (msg.getAuthor().isEmpty()) return Mono.empty();
        if (event.getClient().getSelfId().equals(msg.getAuthor().get().getId())) return Mono.empty();

        String content = msg.getContent();

        if (content.toLowerCase().contains("uwu")) {
            UwuCounter.getInstance().add(msg.getAuthor().get().getId());
        }

        if (content.toLowerCase().contains("owo")) {
            OwoCounter.getInstance().add(msg.getAuthor().get().getId());
        }

        if (content.startsWith(config.getPrefix())) {
            handleCommand(event);
            return Mono.empty();
        }

        if (Util.messageContainsPicture(msg)) {
            MemeStore.getInstance().trackMeme(msg.getId());
        }

        return Mono.empty();
    }

    @Override
    public Publisher<?> onMessageDelete(MessageDeleteEvent event) {
        MemeStore memeStore = MemeStore.getInstance();
        Snowflake id = event.getMessageId();

        if (memeStore.isTrackedFromArchiveId(id))
            memeStore.removeFromArchiveId(id);

        return Mono.empty();
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
        return event.getMessage().map(msg -> {
            sendOrEditMessage(msg, event.getClient());

            return Mono.empty();
        });
    }

    @Override
    public Publisher<?> onReactionRemove(ReactionRemoveEvent event) {
        return event.getMessage().map(msg -> {
            sendOrEditMessage(msg, event.getClient());

            return Mono.empty();
        });
    }

    private void sendOrEditMessage(Message msg, GatewayDiscordClient client) {
        Optional<User> author = msg.getAuthor();

        if (author.isEmpty()) // Empty author, for whatever reason
            return;

        if (author.get().getId().equals(client.getSelfId())) // Don't wanna send messages by us
            return;

        if (!MemeStore.getInstance().isTracked(msg.getId())) // Not tracked? Don't care
            return;

        MemeStore memeStore = MemeStore.getInstance();

        TrackingState state = memeStore.getTrackingState(msg.getId());
        Long upvotes = countUpvotes(msg);
        Long downvotes = countDownvotes(msg);

        if (upvotes - downvotes < config.getRequiredVotes() && state == TrackingState.PENDING_ARCHIVING)
            return;

        // Pending
        if (state == TrackingState.PENDING_ARCHIVING) {
            config.getMemeArchive().getRestChannel().createMessage(makeEmbed(msg, upvotes, downvotes))
                    .subscribe(msgData -> memeStore.promote(msg.getId(), Snowflake.of(msgData.id())));

            return;
        }

        // Update votes
        Snowflake archiveId = memeStore.getArchivedId(msg.getId());

        config.getMemeArchive().getRestChannel().getRestMessage(archiveId).edit(
                MessageEditRequest.builder().embed(makeEmbed(msg, upvotes, downvotes)).build()
        ).subscribe();
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
        embedCreateSpec.setColor(Color.of(0xbaaada));

        return embedCreateSpec.asRequest();
    }

    private void handleCommand(MessageCreateEvent event) {
        Message msg = event.getMessage();
        String content = msg.getContent();

        String[] parts = content.substring(config.getPrefix().length()).split(" ");

        String cmd = parts[0].toLowerCase();
        String fullArgs;
        String[] args = new String[0];

        if (parts.length > 1) {
            fullArgs = content.substring(config.getPrefix().length() + cmd.length() + 1).trim();
            args = fullArgs.split(" ");
        }

        Optional<Member> senderOpt = event.getMember();
        if (senderOpt.isEmpty()) return;
        Member sender = senderOpt.get();
        Snowflake senderId = sender.getId();

        RestChannel channel = msg.getRestChannel();

        boolean senderIsAdmin = config.getAdmins().contains(sender.getId().asString());

        switch (cmd) {
            case "quit" -> {
                if (!senderIsAdmin) return;
                event.getClient().logout().subscribe();
            }
            case "hilfe", "?", "help" -> {
                if (!checkCooldown(channel, sender, "help")) return;

                String version = this.getClass().getPackage().getImplementationVersion();

                if (version == null) version = "Staging";

                channel.createMessage("**__Hilfe__** - *MemerBot " + version + "*\n"
                        + "\n"
                        + "**hilfe**, **help**, **?** - Zeigt dieses Menü an.\n"
                        + "**b**, **bal**, **balance**, **k**, **konto**, **kontostand** "
                            + "- Zeigt deinen Kontostand an.\n"
                        + "**simp** - Juan Borja simpt für dich!\n"
                        + "**fre**, **free**, **freerealestate** - It's free real estate!\n"
                        + "**wetten**, **bet**, **gamble** - Verwette dein Geld!\n"
                        + "**raub**, **rauben**, **rob** - Raube jemanden aus! (Maximal 10%)\n"
                        + "**daily** - Bekomme dein Daily.\n"
                ).subscribe();
            }
            case "b", "bal", "balance", "k", "konto", "kontostand" -> {
                if (!checkCooldown(channel, sender, "balance")) return;

                if (args.length > 1) {
                    msg.addReaction(ReactionEmoji.unicode("❓"));
                }

                ecoDB.setActivatedAt(sender.getId(), "balance", Instant.now());

                Snowflake id = senderId;
                Set<Snowflake> mentions = msg.getUserMentionIds();
                if (mentions.size() > 1) {
                    channel.createMessage("Du kannst nur eine Person angeben.");
                    return;
                } else if (mentions.size() == 1) {
                    id = mentions.iterator().next();
                }
                long balance = ecoDB.getBalance(id);
                channel.createMessage("Du hast **" + balance + "** "
                        + config.getKekwEmoteString() + "s.")
                        .subscribe();
            }
            case "simp" -> {
                if (!checkCooldown(channel, sender, "simp")) return;
                if (!checkArgCount(msg, args, 0)) return;

                ecoDB.setActivatedAt(sender.getId(), "simp", Instant.now());

                long kekws = 400 + random.nextInt(201);

                ecoDB.addFunds(senderId, kekws);

                channel.createMessage("Juan Borja hat für dich gesimped und " +
                        "gab dir " + kekws + " " + config.getKekwEmoteString() + "s!")
                        .subscribe();
            }
            case "fre", "free", "freerealestate" -> {
                if (!checkCooldown(channel, sender, "freerealestate")) return;
                if (!checkArgCount(msg, args, 0)) return;

                ecoDB.setActivatedAt(sender.getId(), "freerealestate", Instant.now());

                long kekws = 200 + random.nextInt(101);

                ecoDB.addFunds(senderId, kekws);

                channel.createMessage("Hier, " + kekws + " "
                        + config.getKekwEmoteString() + "s. *It's free real estate!*")
                        .subscribe();
            }
            case "wetten", "bet", "gamble" -> {
                if (!checkCooldown(channel, sender, cmd)) return;
                if (!checkArgCount(msg, args, 1)) return;

                long bet;

                try {
                    bet = Long.parseLong(args[0]);
                } catch (NumberFormatException e) {
                    msg.addReaction(ReactionEmoji.unicode("❓")).subscribe();
                    return;
                }

                if (bet <= 0) {
                    msg.addReaction(ReactionEmoji.unicode("❓")).subscribe();
                    return;
                }

                if (!ecoDB.checkAndSubtractFunds(senderId, bet)) {
                    channel.createMessage("Du hast nicht genug "
                            + config.getKekwEmoteString() + "s für "
                            + "diesen Wetteinsatz!").subscribe();
                    return;
                }

                ecoDB.setActivatedAt(sender.getId(), "gamble", Instant.now());

                int multThingy = random.nextInt(30);

                if (multThingy < 10) {
                    channel.createMessage("Du hast deinen Einsatz verloren!")
                            .subscribe();
                    return;
                }

                float multiplier = (multThingy - 10) * 0.1f + 1f;
                long kekws = (long) (bet * multiplier);

                ecoDB.addFunds(senderId, kekws);

                channel.createMessage("Du hast **" + kekws + "** "
                        + config.getKekwEmoteString() + "s bekommen!")
                        .subscribe();
            }
            case "raub", "rauben", "rob" -> {
                if (!checkCooldown(channel, sender, "rob")) return;
                if (!checkArgCount(msg, args, 2)) return;

                Snowflake victimId = getPingedId(msg);
                if (victimId == null) return;

                // We already check for this.
                //noinspection ConstantConditions
                Member victim = msg.getGuild().block().getMemberById(victimId).block();

                if (victim == null) {
                    msg.addReaction(ReactionEmoji.unicode("❓")).subscribe();
                    return;
                }

                long amount;

                try {
                    amount = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    msg.addReaction(ReactionEmoji.unicode("❓")).subscribe();
                    return;
                }

                long victimBal = ecoDB.getBalance(victimId);

                long allowedAmount = (long) (victimBal * config.getMaxRobPercentage());

                if (amount > allowedAmount) {
                    channel.createMessage(victim.getDisplayName()
                        + " hat nur " + victimBal + "! Du darfst maximal **"
                        + allowedAmount + "** " + config.getKekwEmoteString()
                        + (allowedAmount > 1 ? "s" : "") + " rauben.").subscribe();

                    return;
                }

                long mult = random.nextInt(95);

                if (mult < 20) {
                    long fine = (long) (ecoDB.getBalance(senderId)
                        * random.nextInt(10) * 0.05);

                    ecoDB.subtractFunds(senderId, fine);

                    channel.createMessage("Als du zu *" + victim.getDisplayName() + "* "
                        + "fuhrst, hat dich die Polizei entdeckt. Sie haben dich "
                        + "erkannt und stellten dir eine Strafe über **"
                        + fine + "** " + config.getKekwEmoteString()
                        + (fine > 1 ? "s" : "") + " aus!").subscribe();

                    return;
                }

                if (mult <= 80) {
                    long actualAmount = (long) (amount * mult * 0.01);
                    ecoDB.subtractFunds(victimId, actualAmount);
                    ecoDB.addFunds(senderId, actualAmount);

                    channel.createMessage("Du konntest *" + victim.getDisplayName() + "* "
                        + "ausrauben, da er*sie aber grade zurückkam, konntest du nur **"
                        + actualAmount + "** " + config.getKekwEmoteString()
                        + (actualAmount > 1 ? "s " : " ")
                        + "mitgehen lassen.").subscribe();

                    return;
                }

                ecoDB.subtractFunds(victimId, amount);
                ecoDB.addFunds(victimId, amount);

                channel.createMessage("Du konntest *" + victim.getDisplayName() + "* "
                    + "erfolgreich ausrauben! Deine Ausbeute "
                    + (amount > 1 ? "sind" : "ist") + " **" + amount
                    + "** " + config.getKekwEmoteString() + (amount > 1 ? "s" : "") + "!");
            }
            case "setbal", "balset" -> {
                if (!senderIsAdmin) return;
                if (!checkArgCount(msg, args, 2)) return;

                Snowflake who = getPingedId(msg);
                if (who == null) return;

                long amount;

                try {
                    amount = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    msg.addReaction(ReactionEmoji.unicode("❓")).subscribe();
                    return;
                }

                this.ecoDB.setFunds(who, amount);

                msg.addReaction(ReactionEmoji.unicode("✅")).subscribe();
            }
            case "daily" -> {
                if (!checkCooldown(channel, sender, "daily")) return;

                ecoDB.setActivatedAt(sender.getId(), "daily", Instant.now());

                long kekws = 4000 + random.nextInt(1001);

                ecoDB.addFunds(senderId, kekws);

                channel.createMessage("Hier, dein Daily! **" + kekws + "** "
                        + config.getKekwEmoteString() + "s!").subscribe();
            }
            case "uwus" -> {
                this.uwuCounter.subtract(senderId);
                long uwus = this.uwuCounter.get(senderId);

                channel.createMessage("Du hast **" + uwus + "** Mal "
                        + " `uwu` geschrieben.").subscribe();
            }
            case "topuwu", "topuwus" -> {
                this.uwuCounter.subtract(senderId);
                if (!checkCooldown(channel, sender, "topuwus")) return;

                channel.type().subscribe();

                List<Map.Entry<Snowflake, Long>> uwus = this.uwuCounter.getTop(0, 10);
                Guild guild = msg.getGuild().block();
                String topStr = buildCounterString(uwus, guild, "UwUs");

                channel.createMessage("**__UwUs__** *(Die Top 10)*\n"
                        + topStr).subscribe();
            }
            case "owos" -> {
                this.owoCounter.subtract(senderId);
                long owos = this.owoCounter.get(senderId);

                channel.createMessage("Du hast **" + owos + "** Mal "
                        + " `owo` geschrieben.").subscribe();
            }
            case "topowo", "topowos" -> {
                this.owoCounter.subtract(senderId);
                if (!checkCooldown(channel, sender, "topowos")) return;

                channel.type().subscribe();

                List<Map.Entry<Snowflake, Long>> owos = this.owoCounter .getTop(0, 10);
                Guild guild = msg.getGuild().block();
                String topStr = buildCounterString(owos, guild, "OwOs");

                channel.createMessage("**__OwOs__** *(Die Top 10)*\n"
                        + topStr).subscribe();
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkCooldown(RestChannel channel, Member member, String cmd) {
        long timePassed = Instant.now().getEpochSecond()
                - ecoDB.getActivatedAt(member.getId(), cmd).getEpochSecond();
        long timeRequired = config.getCooldown(cmd);

        if (timePassed >= timeRequired) {
            return true;
        }

        channel.createMessage("Whoa, ganz ruhig, " + member.getMention() + "! "
                + "Das darfst du erst wieder in "
                + Util.formatTime(timeRequired - timePassed) + "!")
                .subscribe();

        return false;
    }

    private Snowflake getPingedId(Message msg) {
        Optional<Snowflake> victimOpt = msg.getUserMentionIds().stream().findFirst();

        if (victimOpt.isEmpty()) {
            msg.addReaction(ReactionEmoji.unicode("❓")).subscribe();
            return null;
        }

        return victimOpt.get();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean checkArgCount(Message msg, String[] args, int count) {
        if (args.length != count) {
            msg.addReaction(ReactionEmoji.unicode("❓"));
            return false;
        }

        return true;
    }

    public String buildCounterString(List<Map.Entry<Snowflake, Long>> tops, Guild guild, String ofWhat) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Snowflake, Long> entry : tops) {
            Member member = guild.getMemberById(entry.getKey()).block();
            if (member == null) continue;

            sb.append("*")
                    .append(member.getDisplayName())
                    .append("* - **")
                    .append(entry.getValue())
                    .append("** ")
                    .append(ofWhat)
                    .append("\n");
        }

        return sb.toString();
    }
}
