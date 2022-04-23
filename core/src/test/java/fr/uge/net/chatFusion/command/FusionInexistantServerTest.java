package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FusionInexistantServerTest {

    @Test
    public void toBufferIsInWriteMode() {
        FusionInexistantServer fusionInexistantServer = new FusionInexistantServer();
        assertTrue(fusionInexistantServer.toBuffer().position() != 0);
    }

    @Test
    void checkBufferLength() {
        FusionInexistantServer fusionInexistantServer = new FusionInexistantServer();
        var length = Byte.BYTES;
        assertEquals(length, fusionInexistantServer.toBuffer().position());
    }

}
