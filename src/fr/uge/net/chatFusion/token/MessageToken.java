package fr.uge.net.chatFusion.token;

import java.util.Objects;

public record MessageToken(String login, String text) {
    public MessageToken {
        Objects.requireNonNull(login);
        Objects.requireNonNull(text);
    }
}
