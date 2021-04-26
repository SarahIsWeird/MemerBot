package com.sarahisweird.memerbot;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.reaction.ReactionEmoji;

import java.util.List;

public class Config {
    public static final String memeStoreFile = "memes.csv";
    public static final List<String> admins = List.of("116927399760756742", "260473563310587904");

    public static final Snowflake memeArchiveId_release = Snowflake.of("775720910904623135");
    public static final Snowflake memeArchiveId_debug = Snowflake.of("831531360749355078");

    public static final Snowflake updootId = Snowflake.of("826104904057356298");
    public static final ReactionEmoji updootReaction = ReactionEmoji.custom(updootId, "updoot", false);

    public static final Snowflake downdootId = Snowflake.of("826104891843805205");
    public static final ReactionEmoji downdootReaction = ReactionEmoji.custom(downdootId, "downdoot", false);

    // Set on ready
    public static boolean isDebug;
    public static Channel memeArchive;

    private Config() {}
}
