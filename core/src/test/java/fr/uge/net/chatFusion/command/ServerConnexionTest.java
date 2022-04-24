package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerConnexionTest {


    private final Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void toBufferIsInWriteMode() {
        ServerConnexion serverConnexion = new ServerConnexion("Server1",
                new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777)
        );
        assertTrue(serverConnexion.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        var socketAddress = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        ServerConnexion serverConnexion = new ServerConnexion("Server1", socketAddress);
        var length = Byte.BYTES + Integer.BYTES + UTF8.encode("Server1").remaining() + socketAddress.toBuffer().flip().remaining();
        assertEquals(length, serverConnexion.toBuffer().position());
    }
}
