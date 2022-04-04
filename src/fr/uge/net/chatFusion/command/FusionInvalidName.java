package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public record FusionInvalidName() {
    private static final byte OPCODE = 13;

    public ByteBuffer toBuffer(){
        return ByteBuffer.allocate(Byte.BYTES).put(OPCODE);
    }
}
