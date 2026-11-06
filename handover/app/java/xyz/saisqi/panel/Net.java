package xyz.saisqi.panel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Net {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    public static void run(Runnable r) { POOL.execute(r); }
}
