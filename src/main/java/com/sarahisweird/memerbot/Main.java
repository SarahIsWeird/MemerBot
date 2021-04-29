package com.sarahisweird.memerbot;

import com.sarahisweird.memerbot.tracking.MemeStore;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;

public class Main {
    public static void main(String[] args) {
        Config config = Config.getInstance();

        boolean isDebug = (args.length > 0) && args[0].equalsIgnoreCase("debug");

        if (isDebug) {
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

        config.complete(isDebug, client);

        client.getEventDispatcher().on(new EventHandler()).subscribe();

        client.onDisconnect().block();

        MemeStore.getInstance().save();
    }
}
