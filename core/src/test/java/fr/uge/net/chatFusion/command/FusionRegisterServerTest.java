package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class FusionRegisterServerTest {
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void toBufferIsInWriteMode() {
        FusionRegisterServer fusionRegisterServer = new FusionRegisterServer("server1", new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777));
        assertTrue(fusionRegisterServer.toBuffer().position() != 0);
    }

    @Test
    void checkBufferLength() {
        var serverSrc = "server1";
        var socketAddress = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        FusionRegisterServer fusionRegisterServer = new FusionRegisterServer(serverSrc, socketAddress);
        var length = Byte.BYTES + Integer.BYTES + UTF8.encode(serverSrc).remaining() + socketAddress.toBuffer().flip().remaining();
        assertEquals(length, fusionRegisterServer.toBuffer().position());
    }

}
