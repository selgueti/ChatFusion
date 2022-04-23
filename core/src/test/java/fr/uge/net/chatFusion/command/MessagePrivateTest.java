package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MessagePrivateTest {
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void toBufferIsInWriteMode() {
        MessagePrivate messagePrivate = new MessagePrivate("server1", "4l1c€", "server2", "B0B#€", "Hello World!! €#");
        assertTrue(messagePrivate.toBuffer().position() != 0);
    }

    @Test
    void checkBufferLength() {
        var serverSrc = "server1";
        var loginSrc = "4lic€";
        var serverDst = "server2";
        var loginDst = "B0B#€";
        var msg = "Hello World ! #€";
        MessagePrivate messagePrivate = new MessagePrivate(serverSrc, loginSrc, serverDst, loginDst, msg);
        var length = Byte.BYTES + 5 * Integer.BYTES
                + UTF8.encode(serverSrc).remaining()
                + UTF8.encode(loginSrc).remaining()
                + UTF8.encode(serverDst).remaining()
                + UTF8.encode(loginDst).remaining()
                + UTF8.encode(msg).remaining();
        assertEquals(length, messagePrivate.toBuffer().position());
    }

}
