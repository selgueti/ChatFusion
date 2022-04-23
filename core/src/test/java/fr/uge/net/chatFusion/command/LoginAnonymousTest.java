package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class LoginAnonymousTest {

    @Test
    public void toBufferIsInWriteMode() {
        LoginAnonymous login = new LoginAnonymous("Alice");
        assertTrue(login.toBuffer().position() != 0);
    }

    @Test
    void checkBufferLength() {
        String username = "€€$@funky_login@$€€";
        LoginAnonymous login = new LoginAnonymous(username);
        var length = StandardCharsets.UTF_8.encode(username).remaining() + Integer.BYTES + Byte.BYTES;
        assertEquals(length, login.toBuffer().position());
    }

}
