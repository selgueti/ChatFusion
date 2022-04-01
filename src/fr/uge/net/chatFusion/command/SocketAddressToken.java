package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.util.Objects;

public record SocketAddressToken(byte version, byte[] address, int port) {
    public SocketAddressToken {
        Objects.requireNonNull(address);
        if (version != 4 && version != 6) {
            throw new IllegalArgumentException("version should be 4 or 6");
        }
        if (version == 4 && address.length != 4) {
            throw new IllegalArgumentException("ipv4 address is 4 bytes long");
        }
        if (version == 4 && address.length != 16) {
            throw new IllegalArgumentException("ipv6 address is 16 bytes long");
        }
        if(port < 0 || port > 65_535){
            throw new IllegalArgumentException("port must be between 0 and 65_535");
        }
    }

    public ByteBuffer toBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES * (address.length + 1) + Integer.BYTES);
        buffer.put(version).put(address).putInt(port);
        return buffer;
    }
}
