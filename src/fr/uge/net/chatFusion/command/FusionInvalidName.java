package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public record FusionInvalidName() implements Frame {
    private static final byte OPCODE = 13;

    @Override
    public ByteBuffer toBuffer(){
        return ByteBuffer.allocate(Byte.BYTES).put(OPCODE);
    }
}
