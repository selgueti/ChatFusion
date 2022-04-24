package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class LoginAcceptedTest {
    @Test
    public void toBufferIsInWriteMode() {
        LoginAccepted loginAccepted = new LoginAccepted("Server1");
        assertTrue(loginAccepted.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        String serverName = "€€$@Server1@$€€";
        LoginAccepted login = new LoginAccepted(serverName);
        var length = StandardCharsets.UTF_8.encode(serverName).remaining() + Integer.BYTES + Byte.BYTES;
        assertEquals(length, login.toBuffer().position());
    }

}
