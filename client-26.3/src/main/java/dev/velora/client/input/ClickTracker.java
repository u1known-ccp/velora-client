package dev.velora.client.input;

import java.util.ArrayDeque;

/** Counts real mouse clicks of the last second (fed from the mouse mixin, game thread only). */
public final class ClickTracker {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

    private ClickTracker() { }

    public static void onClick(int button) {
        long now = System.currentTimeMillis();
        if (button == 1) LEFT.addLast(now); // SDL3: 1 = left
        else if (button == 3) RIGHT.addLast(now); // 3 = right
    }

    public static int leftCps() { return count(LEFT); }

    public static int rightCps() { return count(RIGHT); }

    private static int count(ArrayDeque<Long> clicks) {
        long limit = System.currentTimeMillis() - 1000L;
        while (!clicks.isEmpty() && clicks.peekFirst() < limit) clicks.pollFirst();
        return clicks.size();
    }
}
