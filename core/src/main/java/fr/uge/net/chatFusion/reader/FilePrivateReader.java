package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FilePrivate;

import java.nio.ByteBuffer;

public class FilePrivateReader implements Reader<FilePrivate> {
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    private BytesReader bytesReader;
    private String serverSrc;
    private String loginSrc;
    private String serverDst;
    private String loginDst;
    private String fileName;
    private int nbBlocks;
    private int blockSize;
    private byte[] bytes;


    private State state = State.WAITING_SERVER_SRC;

    private enum State {
        DONE,
        WAITING_SERVER_SRC,
        WAITING_LOGIN_SRC,
        WAITING_SERVER_DST,
        WAITING_LOGIN_DST,
        WAITING_FILE_NAME,
        WAITING_NB_BLOCKS,
        WAITING_BLOCK_SIZE,
        WAITING_CONTENT,
        ERROR
    }

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

        if (state == State.WAITING_LOGIN_SRC) {
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

        if (state == State.WAITING_SERVER_DST) {
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

        if (state == State.WAITING_LOGIN_DST) {
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
                    state = State.WAITING_FILE_NAME;
                }
            }
        }

        if (state == State.WAITING_FILE_NAME) {
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    fileName = stringReader.get();
                    stringReader.reset();
                    state = State.WAITING_NB_BLOCKS;
                }
            }
        }

        if (state == State.WAITING_NB_BLOCKS) {
            switch (intReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    nbBlocks = intReader.get();
                    intReader.reset();
                    if (nbBlocks < 0) {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    state = State.WAITING_BLOCK_SIZE;
                }
            }
        }

        if (state == State.WAITING_BLOCK_SIZE) {
            switch (intReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    blockSize = intReader.get();
                    intReader.reset();
                    if (blockSize < 0) {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    bytesReader = new BytesReader(blockSize);
                    state = State.WAITING_CONTENT;
                }
            }
        }

        if (state == State.WAITING_CONTENT) {
            switch (bytesReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    bytes = bytesReader.get();
                    bytesReader.reset();
                    state = State.DONE;
                }
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public FilePrivate get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new FilePrivate(serverSrc, loginSrc, serverDst, loginDst, fileName, nbBlocks, blockSize, bytes);
    }

    @Override
    public void reset() {
        stringReader.reset();
        intReader.reset();
        state = State.WAITING_SERVER_SRC;
        serverSrc = null;
        loginSrc = null;
        serverDst = null;
        loginDst = null;
        fileName = null;
        bytes = null;
    }
}
