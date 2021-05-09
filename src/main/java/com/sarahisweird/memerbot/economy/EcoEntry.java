package com.sarahisweird.memerbot.economy;

import discord4j.common.util.Snowflake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class EcoEntry implements Comparable<EcoEntry> {

    private final Snowflake user;
    private long balance;
    private final Map<String, Instant> cooldowns;

    public EcoEntry(Snowflake user, long balance) {
        this.user = user;
        this.balance = balance;
        this.cooldowns = new HashMap<>();
    }

    public long getBalance() {
        return this.balance;
    }

    public Snowflake getUser() {
        return this.user;
    }

    public void setFunds(long funds) {
        this.balance = funds;
    }

    public void addFunds(long funds) {
        this.balance += funds;
    }

    public void subtractFunds(long funds) {
        this.balance -= funds;
    }

    public boolean safeSubtractFunds(long funds) {
        if (this.balance < funds) return false;

        this.subtractFunds(funds);
        return true;
    }

    @Override
    public int compareTo(EcoEntry other) {
        return (int) (this.balance - other.balance);
    }

    public void setActivatedAt(String what, Instant when) {
        this.cooldowns.put(what, when);
    }

    public Instant getActivatedAt(String what) {
        if (!this.cooldowns.containsKey(what)) return Instant.EPOCH;

        return this.cooldowns.get(what);
    }

    public JSONObject serialize() {
        JSONArray cooldownsJson = new JSONArray();
        this.cooldowns.forEach((cmd, when) -> cooldownsJson.put(
                new JSONObject()
                        .put("command", cmd)
                        .put("lastActivated", when.getEpochSecond())
        ));

        return new JSONObject()
                .put("id", this.user.asString())
                .put("balance", this.balance)
                .put("cooldowns", cooldownsJson);
    }

    public static EcoEntry deserialize(JSONObject obj) {
        EcoEntry entry = new EcoEntry(Snowflake.of(obj.getString("id")),
                obj.getLong("balance"));

        obj.getJSONArray("cooldowns").forEach(o ->
            entry.setActivatedAt(((JSONObject) o).getString("command"),
                    Instant.ofEpochSecond(((JSONObject) o).getLong("lastActivated")))
        );

        return entry;
    }
}
