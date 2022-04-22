package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.util.FrameVisitor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ServerConnexion(String name, SocketAddressToken socketAddressToken) implements Frame {
    private static final byte OPCODE = 15;
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    public ServerConnexion {
        Objects.requireNonNull(name);
        Objects.requireNonNull(socketAddressToken);

        if (name.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public ByteBuffer toBuffer() {
        var bbName = UTF8.encode(name);
        var bbSocketAddress = socketAddressToken.toBuffer().flip();
        var buffer = ByteBuffer.allocate(Integer.BYTES + bbName.remaining() + bbSocketAddress.remaining() + Byte.BYTES);

        buffer.put(OPCODE).putInt(bbName.remaining()).put(bbName).put(bbSocketAddress);
        return buffer;
    }

    @Override
    public void accept(FrameVisitor visitor) {
        visitor.visit(this);
    }
}
