package fr.uge.net.chatFusion;

import java.nio.ByteBuffer;

public class PublicMessage implements Command {
    private final String login;
    private final String msg;
    private final byte OPCODE = 4;

    public PublicMessage(String login, String msg) {
        this.login = login;
        this.msg = msg;
    }

    @Override
    public ProcessStatus writeIn(ByteBuffer bufferOut) {
        return null;
    }

    @Override
    public ProcessStatus readFrom(ByteBuffer bufferIn) {
        return null;
    }

    @Override
    public void reset() {

    }

    @Override
    public boolean isTotalyWritten() {
        return false;
    }

    @Override
    public byte getOpcode() {
        return OPCODE;
    }
}
