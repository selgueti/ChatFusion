package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.*;

import java.nio.ByteBuffer;

public class FrameReader implements Reader<Frame> {

    private final BytesReader opcodeReader = new BytesReader(1);
    private final LoginAnonymousReader loginAnonymousReader = new LoginAnonymousReader();
    private final LoginAcceptedReader loginAcceptedReader = new LoginAcceptedReader();
    private final MessagePublicSendReader messagePublicSendReader = new MessagePublicSendReader();
    private final MessagePublicTransmitReader messagePublicTransmitReader = new MessagePublicTransmitReader();
    private final MessagePrivateReader messagePrivateReader = new MessagePrivateReader();
    private final FilePrivateReader filePrivateReader = new FilePrivateReader();
    private final FusionRegisterServerReader fusionRegisterServerReader = new FusionRegisterServerReader();
    private final FusionInitReader fusionInitReader = new FusionInitReader();
    private final FusionRouteTableSendReader fusionRouteTableSendReader = new FusionRouteTableSendReader();
    private final FusionTableRouteResultReader fusionTableRouteResultReader = new FusionTableRouteResultReader();
    private final ServerConnexionReader serverConnexionReader = new ServerConnexionReader();

    private Frame frame;
    private byte opcode;
    private State state = State.WAITING_OPCODE;


    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            //System.out.println("STATE ===== " + state);
            throw new IllegalStateException();
        }
        if (state == State.WAITING_OPCODE) {
            switch (opcodeReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    opcode = opcodeReader.get()[0];
                    opcodeReader.reset();
                    state = State.WAITING_FRAME;
                }
            }
        }

        if (state == State.WAITING_FRAME) {
            switch (opcode) {
                case 0 -> {
                    switch (loginAnonymousReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = loginAnonymousReader.get();
                            loginAnonymousReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 2 -> {
                    switch (loginAcceptedReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = loginAcceptedReader.get();
                            loginAcceptedReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 3 -> {
                    frame = new LoginRefused();
                    state = State.DONE;
                }
                case 4 -> {
                    switch (messagePublicSendReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = messagePublicSendReader.get();
                            messagePublicSendReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 5 -> {
                    switch (messagePublicTransmitReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = messagePublicTransmitReader.get();
                            messagePublicTransmitReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 6 -> {
                    switch (messagePrivateReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = messagePrivateReader.get();
                            messagePrivateReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 7 -> {
                    switch (filePrivateReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = filePrivateReader.get();
                            filePrivateReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 8 -> {
                    switch (fusionRegisterServerReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = fusionRegisterServerReader.get();
                            fusionRegisterServerReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 9 -> {
                    switch (fusionInitReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = fusionInitReader.get();
                            fusionInitReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 10 -> {
                    frame = new FusionInexistantServer();
                    state = State.DONE;
                }
                case 11 -> {
                    frame = new FusionRootTableAsk();
                    state = State.DONE;
                }
                case 12 -> {
                    switch (fusionRouteTableSendReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = fusionRouteTableSendReader.get();
                            fusionRouteTableSendReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 13 -> {
                    frame = new FusionInvalidName();
                    state = State.DONE;
                }
                case 14 -> {
                    switch (fusionTableRouteResultReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = fusionTableRouteResultReader.get();
                            fusionTableRouteResultReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                case 15 -> {
                    switch (serverConnexionReader.process(bb)) {
                        case REFILL -> {
                            return ProcessStatus.REFILL;
                        }
                        case ERROR -> {
                            state = State.ERROR;
                            return ProcessStatus.ERROR;
                        }
                        case DONE -> {
                            frame = serverConnexionReader.get();
                            serverConnexionReader.reset();
                            state = State.DONE;
                        }
                    }
                }
                default -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
            }
        }

        return ProcessStatus.DONE;
    }

    @Override
    public Frame get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return frame;
    }

    @Override
    public void reset() {
        opcodeReader.reset();
        loginAnonymousReader.reset();
        loginAcceptedReader.reset();
        messagePublicSendReader.reset();
        messagePublicTransmitReader.reset();
        messagePrivateReader.reset();
        filePrivateReader.reset();
        fusionRegisterServerReader.reset();
        fusionInitReader.reset();
        fusionRouteTableSendReader.reset();
        fusionTableRouteResultReader.reset();
        serverConnexionReader.reset();
        frame = null;
        state = State.WAITING_OPCODE;
    }

    private enum State {
        DONE, ERROR, WAITING_OPCODE, WAITING_FRAME
    }
}
