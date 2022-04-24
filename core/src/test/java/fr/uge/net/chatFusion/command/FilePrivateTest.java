package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class FilePrivateTest {
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void toBufferIsInWriteMode() {
        FilePrivate filePrivate = new FilePrivate("server1",
                "4l1c€",
                "server2",
                "B0B#€",
                "README.txt",
                1,
                500,
                new byte[500]);
        assertTrue(filePrivate.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        var serverSrc = "server1";
        var loginSrc = "4lic€";
        var serverDst = "server2";
        var loginDst = "B0B#ẫ€";
        var fileame = "README.txt";
        var nbBlocks = 2;
        var blockSize = 500;
        var bytes = new byte[500];
        FilePrivate filePrivate = new FilePrivate(serverSrc, loginSrc, serverDst, loginDst, fileame, nbBlocks, blockSize, bytes);
        var length = Byte.BYTES + 7 * Integer.BYTES
                + UTF8.encode(serverSrc).remaining()
                + UTF8.encode(loginSrc).remaining()
                + UTF8.encode(serverDst).remaining()
                + UTF8.encode(loginDst).remaining()
                + UTF8.encode(fileame).remaining()
                + blockSize * Byte.BYTES;
        assertEquals(length, filePrivate.toBuffer().position());
    }
}
