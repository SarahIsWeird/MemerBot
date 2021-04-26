package com.sarahisweird.memerbot;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.reaction.ReactionEmoji;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Config {
    private static Config instance;

    private JSONObject json;

    private boolean isDebug;
    private Channel memeArchive;
    private String memeStorePath;
    private List<String> admins;
    private ReactionEmoji.Custom upvoteEmote;
    private ReactionEmoji.Custom downvoteEmote;

    private Config() {
        try {
            json = Util.deserializeJSONFromStream(Objects.requireNonNull(Main.class.getResource("config.json")).openStream());
        } catch (IOException | NullPointerException e) {
            System.err.println("Couldn't read the config file!");
            e.printStackTrace();
            System.exit(1);
        }
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

        this.memeArchive = client.getChannelById(Snowflake.of(
                    this.json.getJSONObject("memeArchive").getString(this.isDebug ? "debug" : "release"))
                ).block();

        this.memeStorePath = this.json.getString("memeStorePath");
        this.admins = new ArrayList<>(); // Split it because Java's a dick
        this.json.getJSONArray("admins").forEach(id -> this.admins.add((String) id));

        JSONObject emotes = this.json.getJSONObject("emotes");
        JSONObject upvote = emotes.getJSONObject("upvote");
        JSONObject downvote = emotes.getJSONObject("downvote");

        this.upvoteEmote = ReactionEmoji.custom(Snowflake.of(upvote.getString("id")),
                upvote.getString("name"),
                false);
        this.downvoteEmote = ReactionEmoji.custom(Snowflake.of(downvote.getString("id")),
                downvote.getString("name"),
                false);
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
}
