package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;

public record FusionInexistantServer() implements Frame {
    private final static byte OPCODE = 10;

    @Override
    public ByteBuffer toBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES);
        buffer.put(OPCODE);
        return buffer;
    }
}