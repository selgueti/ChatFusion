package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.LoginAnonymous;

import java.nio.ByteBuffer;

public class LoginAnonymousReader implements Reader<LoginAnonymous> {
    private final StringReader stringReader = new StringReader();
    private State state = State.WAITING_LOGIN;
    private String login;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_LOGIN) {
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    login = stringReader.get();
                    if (login.isEmpty()) {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    state = State.DONE;
                }
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public LoginAnonymous get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new LoginAnonymous(login);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAITING_LOGIN;
        login = null;
    }

    private enum State {
        DONE, WAITING_LOGIN, ERROR
    }
}
