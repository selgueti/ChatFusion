package fr.uge.net.chatFusion.command;

import fr.uge.net.chatFusion.util.FrameVisitor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;


public record FusionRouteTableSend(int nbMembers, Map<String, SocketAddressToken> routes) implements Frame {
    private final static byte OPCODE = 12;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public FusionRouteTableSend {
        Objects.requireNonNull(routes);
        if (nbMembers < 0) {
            throw new IllegalArgumentException("nbMembers can't < 0");
        }
        if (nbMembers > routes.size()) {
            throw new IllegalArgumentException("routes missing");
        }
        if (nbMembers < routes.size()) {
            throw new IllegalArgumentException("too much routes");
        }
    }

    // [12 (OPCODE) nb_members (INT) name_0 (STRING<=30) address0 (SOCKETADDRESS) name_1 …]
    @Override
    public ByteBuffer toBuffer() {
        int bufferSize = 1024;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        buffer.put(OPCODE).putInt(nbMembers);
        for (var servName : routes.keySet()) {
            var bbServName = UTF8.encode(servName);
            var bbSocketAddr = routes.get(servName).toBuffer().flip();
            if (Integer.BYTES + bbServName.remaining() + bbSocketAddr.remaining() > buffer.remaining()) {
                // need to grow buffer
                bufferSize *= 2;
                var tmpBuffer = ByteBuffer.allocate(bufferSize);
                buffer.flip();
                tmpBuffer.put(buffer);
                buffer = tmpBuffer;
            }
            buffer.putInt(bbServName.remaining()).put(bbServName)
                    .put(bbSocketAddr);
        }
        return buffer;
    }

    @Override
    public void accept(FrameVisitor visitor) {
        visitor.visit(this);
    }
}




