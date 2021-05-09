package com.sarahisweird.memerbot.tracking;

import com.sarahisweird.memerbot.Util;
import discord4j.common.util.Snowflake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Counter {
    protected final Map<Snowflake, Long> counts;
    private final File dbFile;

    protected Counter(File dbFile) {
        this.counts = new HashMap<>();
        this.dbFile = dbFile;
    }

    protected void load() throws IOException {
        if (!dbFile.exists()) return;

        JSONArray db = new JSONArray(Files.readString(dbFile.toPath()));

        db.forEach(o -> {
            JSONObject obj = (JSONObject) o;
            this.counts.put(Snowflake.of(obj.getLong("id")), obj.getLong("count"));
        });
    }

    public void save() {
        JSONArray arr = new JSONArray();

        this.counts.forEach((id, count) -> arr.put(
                new JSONObject().put("id", id.asLong()).put("count", count)
        ));

        try {
            Files.writeString(this.dbFile.toPath(), arr.toString());
        } catch (IOException e) {
            System.err.println("Couldn't write database file " + this.dbFile.getName() + "!");
            e.printStackTrace();
        }
    }

    public void add(Snowflake id) {
        if (!this.counts.containsKey(id)) {
            this.counts.put(id, 1L);
            return;
        }

        this.counts.put(id, this.counts.get(id) + 1);
    }

    public void subtract(Snowflake id) {
        if (!this.counts.containsKey(id)) {
            this.counts.put(id, 0L);
            return;
        }

        this.counts.put(id, Math.max(this.counts.get(id) - 1, 0));
    }

    public long get(Snowflake id) {
        return this.counts.getOrDefault(id, 0L);
    }

    public List<Map.Entry<Snowflake, Long>> getTop(int from, int to) {
        return Util.sortMap(this.counts).subList(from, Math.min(to, this.counts.size()));
    }
}
