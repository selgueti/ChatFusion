package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.command.LoginAnonymous;
import fr.uge.net.chatFusion.command.MessagePrivate;
import fr.uge.net.chatFusion.command.MessagePublicSend;
import fr.uge.net.chatFusion.reader.*;
import fr.uge.net.chatFusion.util.StringController;
import fr.uge.net.chatFusion.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.logging.Logger;


public class ClientChatFusion {

    static private final int BUFFER_SIZE = 10_000;
    static private final Logger logger = Logger.getLogger(ClientChatFusion.class.getName());
    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private final String login;
    private final Thread console;
    private final StringController stringController = new StringController();
    private String serverName;
    private Context uniqueContext;

    public ClientChatFusion(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 3) {
            usage();
            return;
        }
        new ClientChatFusion(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : ClientChatFusion login hostname port");
    }

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    if (uniqueContext.isLogged()) {
                        sendInstruction(msg);
                    }
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    /**
     * Send instructions to the selector via stringController and wake it up
     *
     * @param instruction - input instruction
     * @throws InterruptedException - if thread has been interrupted
     */
    private void sendInstruction(String instruction) throws InterruptedException {
        stringController.add(instruction, selector);
        // Cause the exception if the main thread has requested the interrupt
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted by main thread");
        }
    }

    private ByteBuffer parseInstruction(String instruction) {
        if (!instruction.startsWith("/") && !instruction.startsWith("@")) {
            var msg = instruction.subSequence(1, instruction.length());
            return new MessagePublicSend(serverName, login, msg.toString()).toBuffer();
        } else {
            // consider for moment just private message
            //if(instruction.startsWith("@")) {
            instruction = instruction.substring(1);
            var tokens = instruction.split(":");
            var loginDst = tokens[0];
            var serverDst = tokens[1].split(" ", 1)[0];
            var msg = tokens[1].split(" ", 1)[1];
            return new MessagePrivate(serverName, login, serverDst, loginDst, msg).toBuffer();
        }
    }

    /**
     * Processes the command from the messageController
     */
    private void processInstruction() {
        while (stringController.hasString()) {
            uniqueContext.queueCommand(parseInstruction(stringController.poll()));
        }
    }

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key, this);
        key.attach(uniqueContext);
        sc.connect(serverAddress);
        console.start();

        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
                processInstruction();
            } catch (UncheckedIOException tunneled) {
                console.interrupt();
                Thread.currentThread().interrupt();
                throw tunneled.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        // TODO review the treatment of exceptions from method doConnect, doRead, doWrite
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            logger.warning(serverAddress.getHostString() + ":" + serverAddress.getPort() + " is unreachable");
            silentlyClose(key);
            console.interrupt();
            Thread.currentThread().interrupt();
            //throw new UncheckedIOException(ioe);
        }
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
        private Writer writer = null;
        private final BytesReader opcodeReader = new BytesReader(1);
        private final ClientChatFusion client;
        private final LoginAcceptedReader loginAcceptedReader = new LoginAcceptedReader();
        private final MessagePublicSendReader messagePublicSendReader = new MessagePublicSendReader();
        private final MessagePrivateReader messagePrivateReader = new MessagePrivateReader();

        private boolean closed = false;
        private byte currentCommand;
        private ReadingState readingState = ReadingState.WAITING_OPCODE;
        private AuthenticationState authenticationState = AuthenticationState.UNREGISTERED;

        private Context(SelectionKey key, ClientChatFusion client) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.client = client;

            // need to send LOGIN_ANONYMOUS(0) ?
            queueCommand(new LoginAnonymous(client.login).toBuffer());
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         */
        private void processIn() {
            for (; ; ) {
                if (readingState == ReadingState.WAITING_OPCODE) {
                    if (Reader.ProcessStatus.DONE == opcodeReader.process(bufferIn)) {
                        currentCommand = opcodeReader.get()[0];
                        opcodeReader.reset();
                        readingState = ReadingState.PROCESS_IN;
                    }
                }

                if (authenticationState == AuthenticationState.UNREGISTERED && readingState == ReadingState.PROCESS_IN && currentCommand == 2) {
                    switch (loginAcceptedReader.process(bufferIn)) {
                        case REFILL -> {
                            return;
                        }
                        case ERROR -> authenticationState = AuthenticationState.ERROR;
                        case DONE -> {
                            client.serverName = loginAcceptedReader.get().serverName();
                            loginAcceptedReader.reset();
                            authenticationState = AuthenticationState.LOGGED;
                            readingState = ReadingState.WAITING_OPCODE;
                        }
                    }
                }

                if (authenticationState == AuthenticationState.UNREGISTERED && readingState == ReadingState.PROCESS_IN && currentCommand == 3) {
                    logger.info("Username already used, please chose another one");
                    silentlyClose(); // TODO make sure scanner and main thread are closed to
                    client.console.interrupt(); // TODO probably block into scanner, think about this
                }

                if (readingState == ReadingState.PROCESS_IN) {
                    switch (currentCommand) {
                        case 4 -> {
                            switch (messagePublicSendReader.process(bufferIn)) {
                                case REFILL -> {
                                    return;
                                }
                                case ERROR -> readingState = ReadingState.WAITING_OPCODE;
                                case DONE -> {
                                    var messagePublicSend = messagePublicSendReader.get();
                                    messagePublicSendReader.reset();
                                    System.out.println(messagePublicSend.loginSrc() + "@" + messagePublicSend.serverSrc() + " : " + messagePublicSend.msg());
                                    readingState = ReadingState.WAITING_OPCODE;
                                }
                            }
                        }

                        case 5 -> {
                            // TODO log the MESSAGE_PUBLIC_TRANSMIT receiving
                        }
                        case 6 -> {
                            switch (messagePrivateReader.process(bufferIn)) {
                                case REFILL -> {
                                }
                                case ERROR -> readingState = ReadingState.WAITING_OPCODE;
                                case DONE -> {
                                    var messagePrivate = messagePrivateReader.get();
                                    messagePrivateReader.reset();
                                    System.out.println(messagePrivate.loginSrc() + "@" + messagePrivate.serverSrc() + " : " + messagePrivate.msg());
                                    readingState = ReadingState.WAITING_OPCODE;
                                }
                            }
                        }
                        case 7 -> {
                            // TODO build file + log file receiving
                        }
                        case default -> System.out.println("processIn drop paquet....");
                    }
                }
            }
        }

        /**
         * Add a command to the command queue, tries to fill bufferOut and updateInterestOps
         *
         * @param cmd - cmd
         */
        private void queueCommand(ByteBuffer cmd) {
            queue.addLast(cmd);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            while (!queue.isEmpty()) {
                if (writer == null) {
                    var command = queue.peekFirst();
                    writer = new Writer(command);
                    //writer.setInternalBuffer(command);
                }
                writer.fillBuffer(bufferOut);
                if (writer.isDone()) {
                    queue.removeFirst();
                    writer = null;
                    //writer.reset();
                }
            }
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * updateInterestOps and after the call. Also, it is assumed that process has
         * been be called just before updateInterestOps.
         */

        private void updateInterestOps() {
            var interestOps = 0;
            if (!key.isValid()) {
                return;
            }
            if (!closed && bufferIn.hasRemaining()) {
                interestOps |= SelectionKey.OP_READ;
            }
            if (bufferOut.position() != 0) {
                interestOps |= SelectionKey.OP_WRITE;
            }
            if (interestOps == 0) {
                silentlyClose();
                return;
            }
            key.interestOps(interestOps);
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doRead and after the call
         *
         * @throws IOException - If an I/O error occurs
         */
        private void doRead() throws IOException {
            if (-1 == sc.read(bufferIn)) {
                closed = true;
            }
            processIn();
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException - If an I/O error occurs
         */

        private void doWrite() throws IOException {
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect())
                return; // the selector gave a bad hint
            key.interestOps(SelectionKey.OP_READ);
        }

        private boolean isLogged() {
            return authenticationState == AuthenticationState.LOGGED;
        }

        enum ReadingState {
            WAITING_OPCODE,
            PROCESS_IN,
        }

        private enum AuthenticationState {
            UNREGISTERED, LOGGED, ERROR
        }
    }
}