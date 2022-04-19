package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public record FusionRootTableAsk() implements Frame {
    private static final byte OPCODE = 11;

    @Override
    public ByteBuffer toBuffer(){
        return ByteBuffer.allocate(Byte.BYTES).put(OPCODE);
    }
}
