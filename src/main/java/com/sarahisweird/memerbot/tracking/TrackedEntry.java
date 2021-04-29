package com.sarahisweird.memerbot.tracking;

import discord4j.common.util.Snowflake;
import reactor.util.annotation.Nullable;

import java.time.Instant;

public class TrackedEntry {
    private final Snowflake id;
    private Snowflake archivedId;
    private final Instant createdAt;
    private TrackingState state;

    public static final TrackedEntry EMPTY = new TrackedEntry(null, null, TrackingState.NONE, null);

    public TrackedEntry(Snowflake id, Instant createdAt, TrackingState state, @Nullable Snowflake archivedId) {
        this.id = id;
        this.createdAt = createdAt;
        this.state = state;
        this.archivedId = archivedId;
    }

    public TrackedEntry(Snowflake id, Instant createdAt, TrackingState state) {
        this(id, createdAt, state, null);
    }

    public Snowflake getId() {
        return this.id;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public TrackingState getTrackingState() {
        return this.state;
    }

    public Snowflake getArchivedId() {
        return this.archivedId;
    }

    public void promote(Snowflake archivedId) {
        this.state = TrackingState.UPDATING_VOTES;
        this.archivedId = archivedId;
    }
}
