package com.sarahisweird.memerbot.tracking;

import com.sarahisweird.memerbot.Config;
import discord4j.common.util.Snowflake;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MemeStore {
    private final List<TrackedEntry> trackedMemes;

    private static final File memeStoreFile = new File(Config.getInstance().getMemeStorePath());
    private static final int A_DAY = 60 * 60 * 24;

    private static MemeStore instance;

    private MemeStore() {
        this.trackedMemes = new ArrayList<>();

        if (memeStoreFile.exists()) {
            try {
                String savedMemes = Files.readString(memeStoreFile.toPath());

                if (savedMemes.trim().equals(""))
                    return;

                this.load(savedMemes.split("\n"));
            } catch (IOException e) {
                System.err.println("Couldn't read memes from file!");
                e.printStackTrace();
            }
        }
    }

    private void load(String[] savedMemes) {
        for (String savedMeme : savedMemes) {
            String[] data = savedMeme.split(",");

            Snowflake archivedId;
            if (data[3].equals(""))
                archivedId = null;
            else
                archivedId = Snowflake.of(data[3]);

            this.trackedMemes.add(new TrackedEntry(
                    Snowflake.of(data[0]),
                    Instant.ofEpochSecond(Long.parseLong(data[1])),
                    TrackingState.valueOf(data[2]),
                    archivedId
            ));
        }
    }

    public void save() {
        StringBuilder memeCSV = new StringBuilder();

        if (!this.trackedMemes.isEmpty()) {
            for (TrackedEntry trackedEntry : this.trackedMemes) {
                if (memeCSV.length() != 0)
                    memeCSV.append("\n");

                String archivedIdStr;
                Snowflake archivedId = trackedEntry.getArchivedId();

                if (archivedId == null)
                    archivedIdStr = "";
                else
                    archivedIdStr = archivedId.asString();

                memeCSV.append(trackedEntry.getId().asString())
                        .append(',').append(trackedEntry.getCreatedAt().getEpochSecond())
                        .append(',').append(trackedEntry.getTrackingState().toString())
                        .append(',').append(archivedIdStr);
            }
        }

        try {
            Files.writeString(memeStoreFile.toPath(), memeCSV.toString());
        } catch (IOException e) {
            System.err.println("Couldn't write memes to file!");
            e.printStackTrace();
        }
    }

    public static MemeStore getInstance() {
        if (instance == null)
            instance = new MemeStore();

        return instance;
    }

    public void trackMeme(Snowflake messageId) {
        String strId = messageId.asString();

        if (this.isTracked(strId))
            return;

        this.trackedMemes.add(new TrackedEntry(
                messageId,
                Instant.now(),
                TrackingState.PENDING_ARCHIVING
        ));
    }

    public boolean isTracked(Snowflake messageId) {
        return this.isTracked(messageId.asString());
    }

    public boolean isTracked(String messageId) {
        return this.trackedMemes
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(te -> te.getId().asString().equals(messageId));
    }

    public boolean isTrackedFromArchiveId(Snowflake archiveId) {
        return this.trackedMemes
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(te -> te.getArchivedId().equals(archiveId));
    }

    public TrackingState getTrackingState(Snowflake messageId) {
        return this.trackedMemes
                .stream()
                .filter(te -> te.getId().equals(messageId))
                .findAny()
                .orElse(TrackedEntry.EMPTY)
                .getTrackingState();
    }

    public void promote(Snowflake messageId, Snowflake archivedId) {
        this.trackedMemes
                .stream()
                .filter(te -> te.getId().equals(messageId))
                .findAny()
                .orElseThrow()
                .promote(archivedId);
    }

    public Snowflake getArchivedId(Snowflake messageId) {
        return this.trackedMemes
                .stream()
                .filter(te -> te.getId().equals(messageId))
                .findAny()
                .orElseThrow()
                .getArchivedId();
    }

    public void yeetOldOnes() {
        this.trackedMemes.removeIf(te -> Instant.now().getEpochSecond() - te.getCreatedAt().getEpochSecond() > A_DAY);
    }

    public void removeFromArchiveId(Snowflake archiveId) {
        this.trackedMemes.removeIf(te -> te.getArchivedId().equals(archiveId));
    }
}
