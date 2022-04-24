package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketAddressTokenTest {

    public void toBufferIsInWriteMode() {
        SocketAddressToken socketAddressToken = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        assertTrue(socketAddressToken.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        SocketAddressToken socketAddressToken = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        var length = Byte.BYTES + Byte.BYTES * socketAddressToken.address().getAddress().length + Integer.BYTES;
        assertEquals(length, socketAddressToken.toBuffer().position());
    }
}
