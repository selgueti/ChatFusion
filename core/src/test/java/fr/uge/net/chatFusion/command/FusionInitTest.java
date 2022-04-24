package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class FusionInitTest {

    @Test
    public void toBufferIsInWriteMode() {
        FusionInit fusionInit = new FusionInit(new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777));
        assertTrue(fusionInit.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        var socketAddress = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        FusionInit fusionInit = new FusionInit(socketAddress);
        var length = Byte.BYTES + socketAddress.toBuffer().flip().remaining();
        assertEquals(length, fusionInit.toBuffer().position());
    }

}
