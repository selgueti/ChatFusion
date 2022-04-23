package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FusionRouteTableAskTest {

    @Test
    public void toBufferIsInWriteMode() {
        FusionRootTableAsk fusionRouteTableAsk = new FusionRootTableAsk();
        assertTrue(fusionRouteTableAsk.toBuffer().position() != 0);
    }

    @Test
    void checkBufferLength() {
        FusionRootTableAsk fusionRouteTableAsk = new FusionRootTableAsk();
        var length = Byte.BYTES;
        assertEquals(length, fusionRouteTableAsk.toBuffer().position());
    }
}
