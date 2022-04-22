package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.util.FrameVisitor;

import java.nio.ByteBuffer;

public record FusionInvalidName() implements Frame {
    private static final byte OPCODE = 13;

    @Override
    public ByteBuffer toBuffer(){
        return ByteBuffer.allocate(Byte.BYTES).put(OPCODE);
    }

    @Override
    public void accept(FrameVisitor visitor) {
        visitor.visit(this);
    }
}
