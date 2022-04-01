package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.reader.IntReader;
import fr.uge.net.chatFusion.reader.PublicMessageReader;
import fr.uge.net.chatFusion.reader.Reader;

import java.nio.ByteBuffer;

public final class PublicMessage implements Command {
    private String login;
    private String msg;
    private final byte OPCODE = 4;
    private final PublicMessageReader publicMessageReader = new PublicMessageReader();
    //private final PublicMessageWriter publicMessageWriter = new PublicMessageWriter();
    private State state = State.NO_STATE;


    enum State {IS_READ, IS_TOTALLY_WRITTEN, WAITING_WRITE_LOGIN, WAITING_WRITE_MSG, WAITING_READ_LOGIN, WAITING_READ_MSG, ERROR, NO_STATE};

    public PublicMessage(String login, String msg) {
        this.login = login;
        this.msg = msg;
    }

    public String getLogin() {
        return login;
    }

    public String getMsg() {
        return msg;
    }

    public PublicMessage() {
    }
    @Override
    public ProcessStatus writeIn(ByteBuffer bufferOut) {
        return null;
    }

    @Override
    public ProcessStatus readFrom(ByteBuffer bufferIn) {
        if (state == State.IS_READ || state == State.ERROR) {
            throw new IllegalStateException();
        }
        switch (publicMessageReader.process(bufferIn)){
            case REFILL -> {return ProcessStatus.REFILL_INPUT;}
            case ERROR -> {return ProcessStatus.ERROR;}
            case DONE -> {
                var publicMessage = publicMessageReader.get();
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public void reset() {
        publicMessageReader.reset();
    }

    @Override
    public boolean isTotallyWritten() {
        return state == State.IS_TOTALLY_WRITTEN;
    }

    @Override
    public byte getOpcode() {
        return OPCODE;
    }
}
