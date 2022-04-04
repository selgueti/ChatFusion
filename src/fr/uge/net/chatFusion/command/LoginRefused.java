package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public record LoginRefused() {
    private final static byte OPCODE = 3;

    public ByteBuffer toBuffer(){
        return ByteBuffer.allocate(Byte.BYTES).put(OPCODE);
    }
}
