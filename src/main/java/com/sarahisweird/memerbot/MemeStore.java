package com.sarahisweird.memerbot;

import discord4j.common.util.Snowflake;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MemeStore {
    private final List<String> trackedMemes;

    private static final File memeStoreFile = new File(Config.memeStoreFile);

    private static MemeStore instance;

    private MemeStore() {
        this.trackedMemes = new ArrayList<>();

        if (memeStoreFile.exists()) {
            try {
                String[] memeIds = Files.readString(memeStoreFile.toPath()).split("\n");

                if (memeIds.length > 0)
                    trackedMemes.addAll(List.of(memeIds));
            } catch (IOException e) {
                System.err.println("Couldn't read memes to file!");
                e.printStackTrace();
            }
        }
    }

    public void save() {
        StringBuilder memeCSV = new StringBuilder();

        if (!this.trackedMemes.isEmpty()) {
            for (String id : this.trackedMemes) {
                if (memeCSV.length() != 0)
                    memeCSV.append("\n");

                memeCSV.append(id);
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

        this.trackedMemes.add(strId);
    }

    public boolean isTracked(Snowflake messageId) {
        return this.isTracked(messageId.asString());
    }

    public boolean isTracked(String messageId) {
        return this.trackedMemes.contains(messageId);
    }

    public void removeTracking(Snowflake messageId) {
        this.trackedMemes.remove(messageId.asString());
    }
}
