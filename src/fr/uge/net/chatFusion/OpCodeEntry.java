package fr.uge.net.chatFusion;

import java.util.Objects;
import java.util.function.Consumer;

public record OpCodeEntry(Command command, Consumer<Command> handler) {
    public OpCodeEntry {
        Objects.requireNonNull(command);
        Objects.requireNonNull(handler);
    }
}
