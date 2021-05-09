package com.sarahisweird.memerbot.tracking;

import java.io.File;
import java.io.IOException;

public class UwuCounter extends Counter {
    private static UwuCounter instance;

    protected UwuCounter() {
        super(new File("uwus.json"));

        try {
            super.load();
        } catch (IOException e) {
            System.err.println("Couldn't load UwU database!");
            e.printStackTrace();
        }
    }

    public static UwuCounter getInstance() {
        if (instance == null)
            instance = new UwuCounter();

        return instance;
    }
}
