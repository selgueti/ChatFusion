package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.MessagePrivate;

import java.nio.ByteBuffer;

public class MessagePrivateReader implements Reader<MessagePrivate> {

    private final StringReader stringReader = new StringReader();
    private State state = State.WAITING_SERVER_SRC;
    private String serverSrc;
    private String loginSrc;
    private String serverDst;
    private String loginDst;
    private String msg;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_SERVER_SRC) {
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    serverSrc = stringReader.get();
                    stringReader.reset();
                    state = State.WAITING_LOGIN_SRC;
                }
            }
        }

        if(state == State.WAITING_LOGIN_SRC){
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    loginSrc = stringReader.get();
                    stringReader.reset();
                    state = State.WAITING_SERVER_DST;
                }
            }
        }

        if(state == State.WAITING_SERVER_DST){
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    serverDst = stringReader.get();
                    stringReader.reset();
                    state = State.WAITING_LOGIN_DST;
                }
            }
        }

        if(state == State.WAITING_LOGIN_DST){
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    loginDst = stringReader.get();
                    stringReader.reset();
                    state = State.WAITING_MSG;
                }
            }
        }

        if(state == State.WAITING_MSG){
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    msg = stringReader.get();
                    state = State.DONE;
                }
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public MessagePrivate get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new MessagePrivate(serverSrc, loginSrc, serverDst, loginDst, msg);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAITING_SERVER_SRC;
        serverSrc = null;
        loginSrc = null;
        serverDst = null;
        loginDst = null;
        msg = null;
    }

    private enum State {
        DONE, WAITING_SERVER_SRC, WAITING_LOGIN_SRC, WAITING_SERVER_DST, WAITING_LOGIN_DST, WAITING_MSG, ERROR
    }
}
