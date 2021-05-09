package com.sarahisweird.memerbot;

import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Message;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class Util {

    public static boolean messageContainsPicture(Message message) {
        return message.getAttachments().stream().anyMatch(attachment -> attachment.getWidth().isPresent());
    }

    public static Attachment collapseAttachmentSet(Set<Attachment> attachmentSet) {
        return (Attachment) attachmentSet.stream().filter(att -> att.getWidth().isPresent()).toArray()[0];
    }

    public static JSONObject deserializeJSONFromStream(InputStream stream) throws IOException {
        return new JSONObject(new String(stream.readAllBytes()));
    }

    public static long parseDuration(String formatted) {
        byte[] chars = formatted.getBytes();
        long secs = 0;
        long tmp = 0;

        for (byte c : chars) {
            if (Character.isWhitespace(c)) continue;

            if (c >= '0' && c <= '9') {
                tmp = tmp * 10 + (c - '0');
                continue;
            }

            switch (c) {
                case 'd' -> secs += tmp * 24 * 3600;
                case 'h' -> secs += tmp * 3600;
                case 'm' -> secs += tmp * 60;
                case 's' -> secs += tmp;
                default -> throw new IllegalArgumentException(c + " isn't a valid timespan!");
            }

            tmp = 0;
        }

        return secs;
    }

    public static String formatTime(long seconds) {
        StringBuilder sb = new StringBuilder();

        long days = seconds / (24 * 3600);
        if (days > 0)
            sb.append(days).append(" Tag").append(days > 1 ? "en" : "").append(" ");

        seconds -= days * 24 * 3600;

        long hours = seconds / 3600;
        if (hours > 0)
            sb.append(hours).append(" Stunde").append(hours > 1 ? "n" : "").append(" ");

        seconds -= hours * 3600;

        long minutes = seconds / 60;
        if (minutes > 0)
            sb.append(minutes).append(" Minute").append(minutes > 1 ? "n" : "").append(" ");

        seconds -= minutes * 60;

        if (seconds > 0)
            sb.append(seconds).append(" Sekunde").append(seconds > 1 ? "n" : "");

        return sb.toString();
    }

    public static <K, V extends Comparable<V>> List<Map.Entry<K, V>> sortMap(Map<K, V> map) {
        return sortEntryList(new ArrayList<>(map.entrySet()));
    }

    public static <K, V extends Comparable<V>> List<Map.Entry<K, V>> sortEntryList(List<Map.Entry<K, V>> list) {
        List<Map.Entry<K, V>> left = new ArrayList<>();
        List<Map.Entry<K, V>> right = new ArrayList<>();

        Map.Entry<K, V> pivot = list.get(list.size() - 1);

        for (Map.Entry<K, V> l : list) {
            if (pivot.getValue().compareTo(l.getValue()) > 0)
                left.add(l);
            else if (!l.getKey().equals(pivot.getKey()))
                right.add(l);
        }

        if (left.size() > 1)
            left = sortEntryList(left);

        if (right.size() > 1)
            right = sortEntryList(right);

        left.add(pivot);
        left.addAll(right);
        return left;
    }

    private Util() {}
}
