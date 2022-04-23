package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.LoginAccepted;

import java.nio.ByteBuffer;

public class LoginAcceptedReader implements Reader<LoginAccepted>{

    private final StringReader stringReader = new StringReader();
    private State state = State.WAITING_SERVER_NAME;
    private String serverName;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_SERVER_NAME) {
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    serverName = stringReader.get();
                    state = State.DONE;
                }
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public LoginAccepted get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new LoginAccepted(serverName);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAITING_SERVER_NAME;
        serverName = null;
    }

    private enum State {
        DONE, WAITING_SERVER_NAME, ERROR
    }
}
