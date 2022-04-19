package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public record LoginRefused() implements Frame {
    private final static byte OPCODE = 3;

    @Override
    public ByteBuffer toBuffer(){
        return ByteBuffer.allocate(Byte.BYTES).put(OPCODE);
    }
}
