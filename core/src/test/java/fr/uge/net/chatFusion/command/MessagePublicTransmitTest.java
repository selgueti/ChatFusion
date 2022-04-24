package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MessagePublicTransmitTest {
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void toBufferIsInWriteMode() {
        MessagePublicTransmit messagePublicTransmit = new MessagePublicTransmit("server1", "4l1c€", "Hello World!! €#");
        assertTrue(messagePublicTransmit.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        var serverSrc = "server1";
        var loginSrc = "4lic€";
        var msg = "Hello World ! #€";
        MessagePublicTransmit messagePublicTransmit = new MessagePublicTransmit(serverSrc, loginSrc, msg);
        var length = Byte.BYTES + 3 * Integer.BYTES + UTF8.encode(serverSrc).remaining() + UTF8.encode(loginSrc).remaining() + UTF8.encode(msg).remaining();
        assertEquals(length, messagePublicTransmit.toBuffer().position());
    }

}
