package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.util.StringController;
import fr.uge.net.chatFusion.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerFusionManager {
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerFusionManager.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Thread console;
    private final StringController stringController = new StringController();


    public ServerFusionManager(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.console = new Thread(this::consoleRun);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerFusionManager(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerFusionManager port");
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                            Console Thread + management of the user's instructions                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
                    sendInstruction(command);
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
     * @param instruction - user input
     * @throws InterruptedException - if thread has been interrupted
     */
    private void sendInstruction(String instruction) throws InterruptedException {
        stringController.add(instruction, selector);
        // Cause the exception if the main thread has requested the interrupt
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted by main thread");
        }
    }

    /**
     * Processes the command from the messageController
     */
    private void processInstructions() throws IOException {
        while (stringController.hasString()) {
            treatInstruction(stringController.poll());
        }
    }

    private void treatInstruction(String command) throws IOException {
        switch (command) {
            case "INFO" -> processInstructionInfo();
            case "SHUTDOWN" -> processInstructionShutdown();
            case "SHUTDOWNNOW" -> processInstructionShutdownNow();
            default -> System.out.println("Unknown command");
        }
    }

    private int nbServerActuallyConnected() {
        return selector.keys().stream().mapToInt(key -> {
            if (key.isValid() && key.channel() != serverSocketChannel) {
                return 1;
            } else {
                return 0;
            }
        }).sum();
    }

    private void processInstructionInfo() {
        logger.info("There are currently " + nbServerActuallyConnected() + " server connected to SFM");
    }

    private void processInstructionShutdown() {
        logger.info("shutdown...");
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            logger.severe("IOESHUTDOWN" + e.getCause());
        }
    }

    private void processInstructionShutdownNow() throws IOException {
        logger.info("shutdown now...");
        for (SelectionKey key : selector.keys()) {
            if (key.channel() != serverSocketChannel && key.isValid()) {
                key.channel().close();
            }
        }
        console.interrupt();
        Thread.currentThread().interrupt();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                selection loop                                                  //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        console.start();
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                processInstructions();
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
        selectionKey.attach(new Context(selectionKey, this));
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           sfm commands processing                                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // TODO add method for sfm commands processing


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                   Context: represents the state of a discussion with a specific interlocutor                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ServerFusionManager server; // we could also have Context as an instance class, which would naturally
        // give access to ServerFusionManager.this
        private boolean closed = false;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queueCommand = new ArrayDeque<>();
        private Writer writer = null;

        private Context(SelectionKey key, ServerFusionManager server) {
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
                //TODO
            }
        }

        /**
         * Add a text to the text queue, tries to fill bufferOut and updateInterestOps
         *
         * @param cmd - command to add to the command queue
         */
        private void queueCommand(ByteBuffer cmd) {
            queueCommand.addLast(cmd);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the command queue
         */
        private void processOut() {
            while (!queueCommand.isEmpty()) {
                if (writer == null) {
                    var command = queueCommand.peekFirst();
                    writer = new Writer(command);
                }
                writer.fillBuffer(bufferOut);
                if (writer.isDone()) {
                    queueCommand.removeFirst();
                    writer = null;
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