package fr.uge.net.chatFusion.command;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

public class LoginRefusedTest {
    @Test
    public void toBufferIsInWriteMode() {
        LoginRefused loginRefused = new LoginRefused();
        assertTrue(loginRefused.toBuffer().position() != 0);
    }

    @Test
    public void checkBufferLength() {
        LoginRefused loginRefused = new LoginRefused();
        var length = Byte.BYTES;
        assertEquals(length, loginRefused.toBuffer().position());
    }

}
