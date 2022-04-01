package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.util.StringController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
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
    private Context uniqueContext;


    //private final Map<Byte, OpCodeEntry> commandMap = new HashMap<>();
    //private final Map<Byte, Consumer<?super Command>> commandHandler = new HashMap<>();


    public ClientChatFusion(String login, InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        //setOpCodeEntries();
    }

    /*private void setOpCodeEntries(){
        commandMap.put((byte)4, new OpCodeEntry(new PublicMessage(), (pm) -> {
            switch (pm){
                case PublicMessage _pm -> System.out.println(_pm.getLogin() + " : " + _pm.getMsg());
                case default -> {} // just ignore
            }
        }));
    }*/

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
                    sendCommand(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    /**
     * Send instructions to the selector via messageController and wake it up
     *
     * @param msg - msg
     * @throws InterruptedException - if thread has been interrupted
     */
    private void sendCommand(String msg) throws InterruptedException {
        stringController.add(msg, selector);
        // Cause the exception if the main thread has requested the interrupt
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted by main thread");
        }
    }

    /**
     * Processes the command from the messageController
     */
    private void processCommands() {
        while (stringController.hasString()) {
            // need to recognize command and call the good constructor
            //uniqueContext.queueCommand(new PublicMessage(login, stringController.poll()));
            //uniqueContext.queueCommand(new MessageWriter(login, stringController.poll()));
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
                processCommands();
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
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            logger.warning(serverAddress.getHostString() + ":" + serverAddress.getPort() + " is unreachable");
            silentlyClose(key);
            console.interrupt();
            Thread.currentThread().interrupt();
            //throw new UncheckedIOException(ioe);
        }

        try {
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            logger.info("I/O error while sending a message");
            //silentlyClose(key);
            //throw new UncheckedIOException(ioe);
        }

        try {
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            logger.info("I/O error while receiving a message");
            //silentlyClose(key);
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
        //private final ArrayDeque<Command> queue = new ArrayDeque<>();
        private boolean closed = false;
        private State state = State.WAITING_OPCODE;
        private byte currentProcess;
        private final ClientChatFusion client;

        enum State {
            WAITING_OPCODE,
            PROCESS_IN
        }

        private Context(SelectionKey key, ClientChatFusion client) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.client = client;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         */
        private void processIn() {
            for (; ; ) {
                if(state == State.WAITING_OPCODE && bufferIn.position() != 0){
                    currentProcess = bufferIn.get();
                    state = State.PROCESS_IN;
                }
                if(state == State.PROCESS_IN){
                    /*if(client.commandMap.containsKey(currentProcess)){
                        // Read the command
                        var readStatus = client.commandMap.get(currentProcess).command().readFrom(bufferIn);
                        switch (readStatus) {
                            case DONE:
                                var opCodeEntry = client.commandMap.get(currentProcess);

                                // applies the processing for this order
                                client.commandMap.get(opCodeEntry.command().getOpcode()).handler().accept(client.commandMap.get(opCodeEntry.command().getOpcode()).command());
                                opCodeEntry.command().reset(); // for next reading
                                state = State.WAITING_OPCODE;
                                break;
                            case REFILL_INPUT:
                                return;

                            case ERROR:
                                //silentlyClose(); // not for a client, need to think about this
                                return;
                        }
                    }
                     */
                }
            }
        }

        /**
         * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
         *
         * @param cmd - cmd
         */
        /*private void queueCommand(Command cmd) {
            queue.addLast(cmd);
            processOut();
            updateInterestOps();
        }*/

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            /*while (!queue.isEmpty()) {
                var command = queue.peekFirst();
                command.writeIn(bufferOut);
                if (command.isTotallyWritten()) {
                    queue.removeFirst();
                }
            }*/
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
    }
}