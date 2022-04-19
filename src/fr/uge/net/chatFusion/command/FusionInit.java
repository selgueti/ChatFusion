package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
}
