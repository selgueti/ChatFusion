package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.reader.MessageReader;
import fr.uge.net.chatFusion.reader.Reader;
import fr.uge.net.chatFusion.writer.MessageWriter;
import fr.uge.net.chatFusion.util.StringController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatFusion {
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChatFusion.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final InetSocketAddress sfmAddress;
    private final Selector selector;
    private final String name;
    private final Thread console;
    private final StringController stringController = new StringController();

    public ServerChatFusion(String name, int port, InetSocketAddress sfmAddress) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.sfmAddress = sfmAddress;
        this.name = name;
        this.console = new Thread(this::consoleRun);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new ServerChatFusion(args[0], Integer.parseInt(args[1]), new InetSocketAddress(args[2], Integer.parseInt(args[3]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerChatFusion name port address_sfm port_sfm");
    }

    @Override
    public String toString() {
        return "ServerChatFusion{" +
                "name=" + name +
                ", sfmAddress=" + sfmAddress +
                '}';
    }

    private void consoleRun() {
        boolean closed = false;
        try {
            try (var scanner = new Scanner(System.in)) {
                while (!closed && scanner.hasNextLine()) {
                    var command = scanner.nextLine();
                    if (command.equals("SHUTDOWNNOW")) {
                        closed = true;
                        scanner.close();
                    }
                    sendCommand(command);
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
     * @param command - command
     * @throws InterruptedException - if thread has been interrupted
     */
    private void sendCommand(String command) throws InterruptedException {
        stringController.add(command);
        selector.wakeup();
        // Cause the exception if the main thread has requested the interrupt
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted by main thread");
        }
    }

    /**
     * Processes the command from the messageController
     */
    private void processCommands() throws IOException {
        while (stringController.hasString()) {
            treatCommand(stringController.poll());
        }
    }

    private void treatCommand(String command) throws IOException {
        switch (command) {
            case "INFO" -> processInfo();
            case "SHUTDOWN" -> processShutdown();
            case "SHUTDOWNNOW" -> processShutdownNow();
            default -> System.out.println("Unknown command");
        }
    }

    private int nbClientActuallyConnected() {
        return selector.keys().stream().mapToInt(key -> {
            if (key.isValid() && key.channel() != serverSocketChannel) {
                return 1;
            } else {
                return 0;
            }
        }).sum();
    }

    private void processInfo() {
        logger.info("There are currently " + nbClientActuallyConnected() + " clients connected to the server");
    }

    private void processShutdown() {
        logger.info("shutdown...");
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            logger.severe("IOESHUTDOWN" + e.getCause());
        }
    }

    private void processShutdownNow() throws IOException {
        logger.info("shutdown now...");
        for (SelectionKey key : selector.keys()) {
            if (key.channel() != serverSocketChannel && key.isValid()) {
                key.channel().close();
            }
        }
        console.interrupt();
        Thread.currentThread().interrupt();
    }


    public void launch() throws IOException {
        System.out.println(this);
        console.start();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // TODO connect to SFM and send the FUSION_REGISTER_SERVER(8) command

        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                processCommands();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException", e);
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        var sc = serverSocketChannel.accept();
        if (sc == null) {
            logger.info("Selector lied, no accept");
            return;
        }
        sc.configureBlocking(false);
        var selectionKey = sc.register(selector, SelectionKey.OP_READ);
        selectionKey.attach(new Context(this, selectionKey));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    /**
     * Add a text to all connected clients queue
     *
     * @param msg - text to add to all connected clients queue
     */
    private void broadcast(MessageWriter msg) {
        Objects.requireNonNull(msg);
        for (SelectionKey key : selector.keys()) {
            if (key.channel() == serverSocketChannel) {
                continue;
            }
            Context context = (Context) key.attachment(); // Safe Cast
            context.queueMessage(new MessageWriter(msg));
        }
    }

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<MessageWriter> queue = new ArrayDeque<>();
        private final ServerChatFusion server; // we could also have Context as an instance class, which would naturally
        private final MessageReader messageReader = new MessageReader();
        // give access to ServerChatInt.this
        private boolean closed = false;

        private Context(ServerChatFusion server, SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            for (; ; ) {
                Reader.ProcessStatus status = messageReader.process(bufferIn);
                switch (status) {
                    case DONE:
                        var message = messageReader.get();
                        server.broadcast(message);
                        messageReader.reset();
                        break;
                    case REFILL:
                        return;
                    case ERROR:
                        silentlyClose();
                        return;
                }
            }
        }

        /**
         * Add a text to the text queue, tries to fill bufferOut and updateInterestOps
         *
         * @param msg - text to add to the text queue
         */
        public void queueMessage(MessageWriter msg) {
            queue.addLast(msg);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the text queue
         */
        private void processOut() {
            while (!queue.isEmpty()) {
                var message = queue.peekFirst();
                message.fillBuffer(bufferOut);
                if (message.isDone()) {
                    queue.removeFirst();
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
         * @throws IOException - if some I/O error occurs
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
         * @throws IOException - if some I/O error occurs
         */
        private void doWrite() throws IOException {
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }
    }
}