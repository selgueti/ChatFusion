package fr.uge.net.chatFusion.util;

import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

/**
 * Thread safe class to control string input
 */
public class StringController {
    private final Queue<String> stringQueue = new ArrayDeque<>(8);
    private int pendingMessage = 0;

    public void add(String msg,  Selector selector) {
        Objects.requireNonNull(msg);
        synchronized (stringQueue) {
            stringQueue.add(msg);
            pendingMessage++;
            selector.wakeup();
        }
    }

    /**
     * Assumes that the queue contains at least the given string.
     * */
    public String poll() {
        synchronized (stringQueue) {
            String msg = stringQueue.poll();
            if (msg == null) {
                throw new AssertionError("the selector should not have been woken up");
            }
            pendingMessage--;
            return msg;
        }
    }

    public boolean hasString() {
        synchronized (stringQueue) {
            return pendingMessage > 0;
        }
    }
}

