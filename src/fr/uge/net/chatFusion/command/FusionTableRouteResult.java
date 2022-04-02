package fr.uge.net.chatFusion.command;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public record FusionTableRouteResult(int nbMembers, Map<String, SocketAddressToken> routes) {

    private final static byte OPCODE = 14;
    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public FusionTableRouteResult {
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

    // [14 (OPCODE) nb_members (INT) name_0 (STRING<=30) address1 (SOCKETADDRESS) name_1 …]
    public ByteBuffer toBuffer() {
        int bufferSize = 1024;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        buffer.put(OPCODE).putInt(nbMembers);
        for (var servName : routes.keySet()) {
            var bbServName = UTF8.encode(servName);
            var bbSocketAddr = routes.get(servName).toBuffer();
            if (Integer.BYTES + bbServName.remaining() + bbSocketAddr.remaining() > buffer.remaining()) {
                //need to grow buffer
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
}