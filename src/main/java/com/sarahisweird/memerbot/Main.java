package com.sarahisweird.memerbot;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;

public class Main {
    public static void main(String[] args) {
        MemeStore memeStore = MemeStore.getInstance();

        Config.isDebug = (args.length > 0) && args[0].equalsIgnoreCase("debug");

        if (Config.isDebug) {
            System.out.println("Running in debug mode!");
        }

        GatewayDiscordClient client = DiscordClientBuilder.create(System.getenv("memerbot_token"))
                .build()
                .gateway()
                .login()
                .block();

        if (client == null) {
            System.err.println("Couldn't create bot!");
            System.exit(1);
        }

        client.getEventDispatcher().on(new EventHandler()).subscribe();

        client.onDisconnect().block();

        memeStore.save();
    }
}
