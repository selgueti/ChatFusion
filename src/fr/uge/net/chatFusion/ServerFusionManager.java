package fr.uge.net.chatFusion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerFusionManager {
    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        private boolean closed = false;

        private Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and the ByteBuffer buffer.
         *
         * The convention is that buff is in write-mode.
         */
        private void updateInterestOps() {
            var interestOps = 0;
            if(!closed && buffer.hasRemaining()){
                interestOps |= SelectionKey.OP_READ;
            }
            if(buffer.position() != 0){
                interestOps |= SelectionKey.OP_WRITE;
            }
            if(interestOps == 0){
                silentlyClose();
                return;
            }
            key.interestOps(interestOps);
        }

        /**
         * Performs the read action on sc
         *
         * The convention is that buffer is in write-mode before calling doRead and is in
         * write-mode after calling doRead
         *
         * @throws IOException - if some I/O error occurs
         */
        private void doRead() throws IOException {
            if(-1 == sc.read(buffer)){
                closed = true;
                logger.info("Connexion closed");
            }
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         *
         * The convention is that buffer is in write-mode before calling doWrite and is in
         * write-mode after calling doWrite
         *
         * @throws IOException - if some I/O error occurs
         */
        private void doWrite() throws IOException {
            buffer.flip(); // need to flip buffer to write data
            sc.write(buffer);
            buffer.compact(); // to follow the convention
            updateInterestOps();
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }
    }

    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerFusionManager.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    public ServerFusionManager(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
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
        if(sc == null){
            logger.info("Selector lied, no accept");
            return;
        }
        sc.configureBlocking(false);
        var selectionKey = sc.register(selector, SelectionKey.OP_READ);
        selectionKey.attach(new Context(selectionKey));
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
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
}