package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.util.FrameVisitor;

import java.nio.ByteBuffer;
import java.util.Objects;

public record FusionInit(SocketAddressToken address) implements Frame {
    private static final byte OPCODE = 9;

    public FusionInit{
        Objects.requireNonNull(address);
    }

    @Override
    public ByteBuffer toBuffer(){
        var bbAddress = address.toBuffer().flip();
        ByteBuffer buffer = ByteBuffer.allocate(bbAddress.remaining() + Byte.BYTES);
        buffer.put(OPCODE);
        buffer.put(bbAddress);
        return buffer;
    }

    @Override
    public void accept(FrameVisitor visitor) {
        visitor.visit(this);
    }
}
