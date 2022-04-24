package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FusionInvalidNameTest {

    @Test
    public void toBufferIsInWriteMode() {
        FusionInvalidName fusionInvalidName = new FusionInvalidName();
        assertTrue(fusionInvalidName.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        FusionInvalidName fusionRouteTableSend = new FusionInvalidName();
        var length = Byte.BYTES;
        assertEquals(length, fusionRouteTableSend.toBuffer().position());
    }
}
