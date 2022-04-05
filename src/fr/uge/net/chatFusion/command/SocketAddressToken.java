package fr.uge.net.chatFusion.command;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

public record SocketAddressToken(InetAddress address, int port) {
    public SocketAddressToken {
        Objects.requireNonNull(address);
        if(port < 0 || port > 65_535){
            throw new IllegalArgumentException("port must be between 0 and 65_535");
        }
    }

    public ByteBuffer toBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES * (address.getAddress().length + 1) + Integer.BYTES);
        buffer.put((byte)address.getAddress().length).put(address.getAddress()).putInt(port);
        return buffer;
    }
}
