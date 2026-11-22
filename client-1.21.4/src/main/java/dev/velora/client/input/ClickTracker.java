package dev.velora.client.input;

import java.util.ArrayDeque;

/** Counts real mouse clicks of the last second (fed from the mouse mixin, game thread only). */
public final class ClickTracker {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

    private ClickTracker() { }

    public static void onClick(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) LEFT.addLast(now);
        else if (button == 1) RIGHT.addLast(now);
    }

    public static int leftCps() { return count(LEFT); }

    public static int rightCps() { return count(RIGHT); }

    private static int count(ArrayDeque<Long> clicks) {
        long limit = System.currentTimeMillis() - 1000L;
        while (!clicks.isEmpty() && clicks.peekFirst() < limit) clicks.pollFirst();
        return clicks.size();
    }
}
