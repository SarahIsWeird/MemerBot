package com.sarahisweird.memerbot;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.reaction.ReactionEmoji;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalTime;
import java.util.*;

public class Config {
    private static Config instance;

    private final Random random;

    private JSONObject json;

    private boolean isDebug;
    private Channel memeArchive;
    private String memeStorePath;
    private List<String> admins;
    private ReactionEmoji.Custom upvoteEmote;
    private ReactionEmoji.Custom downvoteEmote;
    private String kekwEmoteString;
    private int requiredVotes;
    private String prefix;
    private List<String> titles;
    private String upvoteText;
    private String downvoteText;
    private String morningText;
    private String noonText;
    private String afternoonText;
    private String eveningText;
    private String nightText;
    private Map<String, Long> cooldowns;
    private double maxRobPercentage;

    private Config() {
        try {
            json = Util.deserializeJSONFromStream(Objects.requireNonNull(Main.class.getResource("config.json")).openStream());
        } catch (IOException | NullPointerException e) {
            System.err.println("Couldn't read the config file!");
            e.printStackTrace();
            System.exit(1);
        }

        this.random = new Random();
    }

    public static Config getInstance() {
        if (instance == null)
            instance = new Config();

        return instance;
    }

    /**
     * Calling getter functions from the config isn't valid until this function has been executed!
     * @param isDebug Are we running in a debug environment?
     * @param client The Bot instance
     */
    public void complete(boolean isDebug, GatewayDiscordClient client) {
        this.isDebug = isDebug;

        String selector = this.isDebug ? "debug" : "release";

        this.memeArchive = client.getChannelById(Snowflake.of(
                    this.json.getJSONObject("memeArchive").getString(selector))
                ).block();

        this.memeStorePath = this.json.getString("memeStorePath");

        this.admins = new ArrayList<>(); // Split it because Java's a dick
        this.json.getJSONArray("admins").forEach(id -> this.admins.add((String) id));

        this.titles = new ArrayList<>(); // Ditto
        this.json.getJSONArray("titles").forEach(id -> this.titles.add((String) id));

        this.cooldowns = new HashMap<>(); // Tritto? lol
        this.json.getJSONArray("cooldowns").forEach(o -> {
            JSONObject obj = (JSONObject) o;
            this.cooldowns.put(obj.getString("command"),
                    Util.parseDuration(obj.getString("cooldown")));
        });

        JSONObject emotes = this.json.getJSONObject("emotes");
        JSONObject upvote = emotes.getJSONObject("upvote");
        JSONObject downvote = emotes.getJSONObject("downvote");
        JSONObject kekw = emotes.getJSONObject("kekw");

        this.upvoteEmote = ReactionEmoji.custom(Snowflake.of(upvote.getString("id")),
                upvote.getString("name"),
                false);
        this.downvoteEmote = ReactionEmoji.custom(Snowflake.of(downvote.getString("id")),
                downvote.getString("name"),
                false);
        this.kekwEmoteString = "<:kekw:" + kekw.getString(selector) + ">";

        this.requiredVotes = this.json.getJSONObject("requiredVotes").getInt(selector);

        this.prefix = this.json.getJSONObject("prefix").getString(selector);

        JSONObject text = this.json.getJSONObject("text");

        this.upvoteText = text.getString("upvotes");
        this.downvoteText = text.getString("downvotes");

        JSONObject timeTexts = text.getJSONObject("time");

        this.morningText = timeTexts.getString("morning");
        this.noonText = timeTexts.getString("noon");
        this.afternoonText = timeTexts.getString("afternoon");
        this.eveningText = timeTexts.getString("evening");
        this.nightText = timeTexts.getString("night");

        this.maxRobPercentage = this.json.getDouble("maxRobPercentage");
    }

    public boolean isDebug() {
        return this.isDebug;
    }

    public Channel getMemeArchive() {
        return this.memeArchive;
    }

    public String getMemeStorePath() {
        return this.memeStorePath;
    }

    public List<String> getAdmins() {
        return this.admins;
    }

    public ReactionEmoji.Custom getUpvoteEmote() {
        return this.upvoteEmote;
    }

    public ReactionEmoji.Custom getDownvoteEmote() {
        return this.downvoteEmote;
    }

    public String getKekwEmoteString() {
        return this.kekwEmoteString;
    }

    public int getRequiredVotes() {
        return this.requiredVotes;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public String getRandomTitle(Message msg) {
        String title = this.titles.get(random.nextInt(this.titles.size()));

        if (!title.contains("#"))
            return title;

        Optional<Member> author = msg.getAuthorAsMember().blockOptional();
        if (author.isEmpty())
            return "?";

        while (title.contains("#")) {
            String selector = title.split("#")[1];
            String replacement;

            switch (selector) {
                case "user" -> replacement = author.get().getDisplayName();
                case "time" -> replacement = getTimedText();
                default -> replacement = "";
            }

            title = title.replace("#" + selector + "#", replacement);
        }

        return title;
    }

    public String getUpvoteText() {
        return this.upvoteText;
    }

    public String getDownvoteText() {
        return this.downvoteText;
    }

    private String getTimedText() {
        int hour = LocalTime.now().getHour();

        if (hour >= 6 && hour < 11) {
            return this.morningText;
        } else if (hour < 14) {
            return this.noonText;
        } else if (hour < 18) {
            return this.afternoonText;
        } else if (hour < 22) {
            return this.eveningText;
        } else {
            return this.nightText;
        }
    }

    public long getCooldown(String command) {
        return this.cooldowns.getOrDefault(command, 0L);
    }

    public double getMaxRobPercentage() {
        return this.maxRobPercentage;
    }
}
