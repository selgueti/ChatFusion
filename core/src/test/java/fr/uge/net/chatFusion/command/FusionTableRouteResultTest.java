package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FusionTableRouteResultTest {


    private final Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    public void toBufferIsInWriteMode() {
        FusionTableRouteResult fusionTableRouteResult = new FusionTableRouteResult(1,
                Map.of("server1",
                        new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777)));
        assertTrue(fusionTableRouteResult.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        var socketAddress = new SocketAddressToken(new InetSocketAddress("localhost", 7777).getAddress(), 7777);
        FusionTableRouteResult fusionTableRouteResult = new FusionTableRouteResult(1, Map.of("server1", socketAddress));
        var length = Byte.BYTES + Integer.BYTES * 2 + UTF8.encode("server1").remaining() + socketAddress.toBuffer().flip().remaining();
        assertEquals(length, fusionTableRouteResult.toBuffer().position());
    }
}
