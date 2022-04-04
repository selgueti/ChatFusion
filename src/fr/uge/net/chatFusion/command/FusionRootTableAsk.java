package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public record FusionRootTableAsk() {
    private static final byte OPCODE = 11;

    public ByteBuffer toBuffer(){
        return ByteBuffer.allocate(Byte.BYTES).put(OPCODE);
    }
}
