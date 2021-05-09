package com.sarahisweird.memerbot.tracking;

import java.io.File;
import java.io.IOException;

public class OwoCounter extends Counter {
    private static OwoCounter instance;

    protected OwoCounter() {
        super(new File("owos.json"));

        try {
            super.load();
        } catch (IOException e) {
            System.err.println("Couldn't load OwO database!");
            e.printStackTrace();
        }
    }

    public static OwoCounter getInstance() {
        if (instance == null)
            instance = new OwoCounter();

        return instance;
    }
}
