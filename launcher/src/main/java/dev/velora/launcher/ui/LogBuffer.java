package dev.velora.launcher.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.TextArea;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe game log sink. Lines are queued from any thread and appended to the TextArea
 * in batches 10x per second (keeps the FX thread responsive even with heavy log output).
 */
public final class LogBuffer {
    private static final int MAX_LINES = 4000;
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final TextArea area;

    public LogBuffer(TextArea area) {
        this.area = area;
        Timeline flush = new Timeline(new KeyFrame(Duration.millis(100), e -> flush()));
        flush.setCycleCount(Timeline.INDEFINITE);
        flush.play();
    }

    public void add(String line) {
        pending.add(line);
    }

    private void flush() {
        if (pending.isEmpty()) return;
        StringBuilder batch = new StringBuilder();
        String line;
        boolean trimmed = false;
        while ((line = pending.poll()) != null) {
            lines.addLast(line);
            batch.append(line).append('\n');
            if (lines.size() > MAX_LINES) {
                lines.pollFirst();
                trimmed = true;
            }
        }
        if (trimmed) {
            area.setText(String.join("\n", lines) + "\n");
        } else {
            area.appendText(batch.toString());
        }
        area.setScrollTop(Double.MAX_VALUE);
    }

    public void clear() {
        pending.clear();
        lines.clear();
        area.clear();
    }

    public String text() { return String.join("\n", lines); }
}
