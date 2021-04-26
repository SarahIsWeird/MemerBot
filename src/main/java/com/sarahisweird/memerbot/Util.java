package com.sarahisweird.memerbot;

import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Message;

import java.util.Set;

public class Util {

    public static boolean messageContainsPicture(Message message) {
        return message.getAttachments().stream().anyMatch(attachment -> attachment.getWidth().isPresent());
    }

    public static Attachment collapseAttachmentSet(Set<Attachment> attachmentSet) {
        return (Attachment) attachmentSet.stream().filter(att -> att.getWidth().isPresent()).toArray()[0];
    }
}
