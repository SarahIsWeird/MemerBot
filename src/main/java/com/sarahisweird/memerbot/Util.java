package com.sarahisweird.memerbot;

import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Message;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

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

    private Util() {}
}
