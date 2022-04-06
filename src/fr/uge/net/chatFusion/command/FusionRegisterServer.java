package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;


public record FusionRegisterServer(String name, SocketAddressToken socketAddressToken) {
    private final static byte OPCODE = 8;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public FusionRegisterServer {
        Objects.requireNonNull(name);
        Objects.requireNonNull(socketAddressToken);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("server name should not be empty");
        }
    }

    public ByteBuffer toBuffer() {
        ByteBuffer bbName = UTF8.encode(name);
        ByteBuffer bbSocketAddress = socketAddressToken.toBuffer();
        bbSocketAddress.flip(); // NEED TO BE FLIPPED
        ByteBuffer buffer = ByteBuffer.allocate(bbSocketAddress.remaining() + bbName.remaining() + Integer.BYTES + Byte.BYTES);
        buffer.put(OPCODE).putInt(bbName.remaining()).put(bbName).put(bbSocketAddress);

        //System.out.println("buffer FusionRegisterServer = " + buffer);
        return buffer;
    }

    @Override
    public String toString() {
        return "FusionRegisterServer{" +
                "name='" + name + '\'' +
                ", socketAddressToken=" + socketAddressToken +
                '}';
    }
}
