package com.sarahisweird.memerbot.economy;

import discord4j.common.util.Snowflake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class EcoDB {
    private static EcoDB instance;
    private static final File dbFile = new File("ecoDB.json");

    private final SortedMap<Snowflake, EcoEntry> ecoEntries;

    private EcoDB() {
        this.ecoEntries = new ConcurrentSkipListMap<>();

        this.load();
    }

    public static EcoDB getInstance() {
        if (instance == null)
            instance = new EcoDB();

        return instance;
    }

    private void load() {
        if (!dbFile.exists()) return;

        String dbContents;

        try {
            dbContents = Files.readString(dbFile.toPath());
        } catch (IOException e) {
            System.err.println("Couldn't read the economy database");
            e.printStackTrace();
            return;
        }

        JSONArray entriesJSON = new JSONArray(dbContents);
        entriesJSON.forEach(o -> {
            EcoEntry entry = EcoEntry.deserialize((JSONObject) o);
            this.ecoEntries.put(entry.getUser(), entry);
        });
    }

    public void save() {
        JSONArray entriesJson = new JSONArray();
        this.ecoEntries.forEach((id, entry) -> entriesJson.put(entry.serialize()));

        try {
            Files.writeString(dbFile.toPath(), entriesJson.toString());
        } catch (IOException e) {
            System.err.println("Couldn't write the economy database!");
            e.printStackTrace();
        }
    }

    public void addUser(Snowflake userId, long balance) {
        this.ecoEntries.put(userId, new EcoEntry(userId, balance));
    }

    public void addUser(Snowflake userId) {
        this.addUser(userId, 0);
    }

    public long getBalance(Snowflake userId) {
        this.checkExists(userId);

        return this.ecoEntries.get(userId).getBalance();
    }

    public void setFunds(Snowflake userId, long funds) {
        this.checkExists(userId);

        this.ecoEntries.get(userId).addFunds(funds);
    }

    public void addFunds(Snowflake userId, long funds) {
        this.checkExists(userId);

        this.ecoEntries.get(userId).addFunds(funds);
    }

    public void subtractFunds(Snowflake userId, long funds) {
        this.checkExists(userId);

        this.ecoEntries.get(userId).subtractFunds(funds);
    }

    public boolean checkAndSubtractFunds(Snowflake userId, long funds) {
        this.checkExists(userId);

        return this.ecoEntries.get(userId).safeSubtractFunds(funds);
    }

    public void setActivatedAt(Snowflake userId, String what, Instant when) {
        this.checkExists(userId);

        this.ecoEntries.get(userId).setActivatedAt(what, when);
    }

    public Instant getActivatedAt(Snowflake userId, String what) {
        this.checkExists(userId);

        return this.ecoEntries.get(userId).getActivatedAt(what);
    }

    private void checkExists(Snowflake userId) {
        if (!this.ecoEntries.containsKey(userId))
            this.addUser(userId);
    }
}
