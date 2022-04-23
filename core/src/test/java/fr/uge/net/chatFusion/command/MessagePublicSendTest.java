package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MessagePublicSendTest {
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void toBufferIsInWriteMode() {
        MessagePublicSend messagePublicSend = new MessagePublicSend("server1", "4l1c€", "Hello World!! €#");
        assertTrue(messagePublicSend.toBuffer().position() != 0);
    }

    @Test
    void checkBufferLength() {
        var serverSrc = "server1";
        var loginSrc = "4lic€";
        var msg = "Hello World ! #€";
        MessagePublicSend messagePublicSend = new MessagePublicSend(serverSrc, loginSrc, msg);
        var length = Byte.BYTES + 3 * Integer.BYTES + UTF8.encode(serverSrc).remaining() + UTF8.encode(loginSrc).remaining() + UTF8.encode(msg).remaining();
        assertEquals(length, messagePublicSend.toBuffer().position());
    }

}
