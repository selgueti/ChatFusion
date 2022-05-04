package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.command.*;
import fr.uge.net.chatFusion.reader.*;
import fr.uge.net.chatFusion.util.StringController;
import fr.uge.net.chatFusion.util.Writer;

import static fr.uge.net.chatFusion.reader.Reader.ProcessStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;


public class ClientChatFusion {

    static private final int BUFFER_SIZE = 10_000;
    static private final Logger logger = Logger.getLogger(ClientChatFusion.class.getName());
    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private String login; // no final to avoid restart instead of re-name in case of conflict with server
    private final Thread console;
    private final FileSender fileSender;
    private final StringController stringController = new StringController();
    private final String directory;
    private String serverName;
    private Context uniqueContext;
    private boolean firstSend = true;

    public ByteBuffer buildFileChunk(byte[] data, String serverDst, String loginDst, String fileName, int nbChunk){
        return new FilePrivate(serverName, login, serverDst, loginDst, fileName, nbChunk, data.length, data).toBuffer();
    }

    public ClientChatFusion(String login, InetSocketAddress serverAddress, String directory) throws IOException {
        this.fileSender = new FileSender(this);
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
        this.directory = directory;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new ClientChatFusion(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2])), args[3]).launch();
    }

    private static void usage() {
        System.out.println("Usage : fr.uge.net.chatFusion.clientChatFusion.ClientChatFusion login hostname port directory");
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                            Console Thread + management of the user's instructions                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendInstruction(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
            //Thread.currentThread().interrupt();
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
            return new MessagePublicSend(serverName, login, instruction).toBuffer();
        }
        else {
                var tokens = instruction.substring(1).split(":", 2);
                if (tokens.length != 2){
                    System.out.println("saisie invalide!");
                }
                var loginDst = tokens[0];
                var serverDst = tokens[1].split(" ", 2)[0];

            if(instruction.startsWith("@")) {
                var msg = tokens[1].split(" ", 2)[1];
                return new MessagePrivate(serverName, login, serverDst, loginDst, msg).toBuffer();
            }
            if(instruction.startsWith("/")){
                System.out.println("detected file instruction");
                var file = Path.of(tokens[1].split(" ", 2)[1]);
                try {
                    var fp = new FileSendInfo(file.toString(), loginDst, serverDst, file.getFileName().toString());
                    fileSender.sendNewFile(fp);
                }catch(IOException ioe){
                    System.out.println("invalid file");
                }
                return ByteBuffer.allocate(0);
            }
        }
        return ByteBuffer.allocate(0);
    }

    /**
     * Processes the command from the messageController
     */
    private void processInstruction() {
        while (stringController.hasString()) {
            uniqueContext.queueCommand(parseInstruction(stringController.poll()));
        }
        // check if there is a file piece to send

    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                selection loop                                                  //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key, this);
        key.attach(uniqueContext);
        sc.connect(serverAddress);
        //uniqueContext.queueCommand(new LoginAnonymous(login).toBuffer());

        while (!Thread.interrupted()) {
            //Helpers.printKeys(selector); // for debug
            //System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                if(firstSend){
                    firstSend = false;
                    uniqueContext.queueCommand(new LoginAnonymous(login).toBuffer());
                }
                processInstruction();
            } catch (UncheckedIOException tunneled) {
                console.interrupt();
                Thread.currentThread().interrupt();
                throw tunneled.getCause();
            }
            //System.out.println("Select finished");
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
            //logger.warning(serverAddress.getHostString() + ":" + serverAddress.getPort() + " is unreachable");
            //silentlyClose(key);
            //console.interrupt();
            //Thread.currentThread().interrupt();
            throw new UncheckedIOException(ioe);
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           client commands processing                                           //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private ProcessStatus processInUnregistered(Context context) {
        if (context.currentCommand == 2) {
            switch (context.loginAcceptedReader.process(context.bufferIn)) {
                case ERROR -> {
                    //System.out.println("Case ERROR");
                    return ProcessStatus.ERROR;
                }
                case REFILL -> {
                    //System.out.println("Case REFILL");
                    return ProcessStatus.REFILL;
                }
                case DONE -> {
                    //System.out.println("Case DONE");
                    serverName = context.loginAcceptedReader.get().serverName();
                    context.loginAcceptedReader.reset();
                    System.out.println("Authentication to " + serverName  + " success with login : " + login);
                    console.start();
                    context.authenticationState = Context.AuthenticationState.LOGGED;
                }
            }
        }
        if (context.currentCommand == 3) {
            System.out.println("Username already used, please re-start and chose another one");
            //console.interrupt(); // not need because console was not started yet at this point
            context.silentlyClose();
            Thread.currentThread().interrupt(); // we are in main thread, so this closes the main program.
        }
        context.readingState = Context.ReadingState.WAITING_OPCODE;
        return ProcessStatus.DONE;
    }

    private Integer hashFileInfo(FilePrivate fp){
        return Objects.hash(fp.serverSrc(), fp.loginSrc(), fp.fileName());
    }

    private void writeFilePiece(FilePrivate fp){
        Path filepath = Path.of(directory, fp.fileName());
        try{
            var file = Files.write(filepath, fp.bytes(), StandardOpenOption.APPEND);
        }catch(IOException ioe){
            logger.info("could not write file piece because:" + ioe.getCause());
        }
        return;
    }

    private ProcessStatus processInLogged(Context context) {
        switch (context.currentCommand) {
            case 5 -> {
                switch(context.messagePublicTransmitReader.process(context.bufferIn)){
                    case ERROR -> {return ProcessStatus.ERROR;}
                    case REFILL -> {return ProcessStatus.REFILL;}
                    case DONE -> {
                        var messagePublic = context.messagePublicTransmitReader.get();
                        context.messagePublicTransmitReader.reset();
                        System.out.println(messagePublic.login() + "@" + messagePublic.server() + " PUBLIC: " + messagePublic.msg());
                    }
                }
            }
            case 6 -> {
                switch (context.messagePrivateReader.process(context.bufferIn)) {
                    case ERROR -> {return ProcessStatus.ERROR;}
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var messagePrivate = context.messagePrivateReader.get();
                        context.messagePrivateReader.reset();
                        System.out.println(messagePrivate.loginSrc() + "@" + messagePrivate.serverSrc() + " : " + messagePrivate.msg());
                    }
                }
            }
            case 7 -> {
                // TODO build file + log file receiving
                switch (context.fileReader.process(context.bufferIn)){
                    case ERROR -> {return ProcessStatus.ERROR;}
                    case REFILL -> {return ProcessStatus.REFILL;}
                    case DONE -> {
                        var filePiece = context.fileReader.get();
                        var fileId = hashFileInfo(filePiece);
                        writeFilePiece(filePiece);
                        context.fileTransferProgress.merge(fileId, 1, Integer::sum);
                        if (context.fileTransferProgress.get(fileId) == filePiece.nbBlocks()){
                            System.out.println("received file: " + filePiece.fileName()+ " can be found in folder " + directory);
                        }
                    }
                }
            }
            default -> {
                System.out.println("OPCODE unknown or unexpected, drop command");
                context.silentlyClose();
                Thread.currentThread().interrupt();
                return ProcessStatus.ERROR;
            }
        }
        context.readingState = Context.ReadingState.WAITING_OPCODE;
        return ProcessStatus.DONE;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                   Context: represents the state of a discussion with a specific interlocutor                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ClientChatFusion client;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queueCommand = new ArrayDeque<>();
        private final BytesReader opcodeReader = new BytesReader(1);
        private final LoginAcceptedReader loginAcceptedReader = new LoginAcceptedReader();
        private final MessagePublicTransmitReader messagePublicTransmitReader = new MessagePublicTransmitReader();
        private final MessagePrivateReader messagePrivateReader = new MessagePrivateReader();
        private final FilePrivateReader fileReader = new FilePrivateReader();
        private final Map<Integer, Integer> fileTransferProgress = new HashMap<>();
        private boolean closed = false;
        private Writer writer = null;

        private byte currentCommand;
        private ReadingState readingState = ReadingState.WAITING_OPCODE;
        private AuthenticationState authenticationState = AuthenticationState.UNREGISTERED;

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
                switch(assureCurrentCommandSet()){
                    case ERROR -> {silentlyClose(); return;}
                    case REFILL -> {return;}
                    case DONE -> {
                        switch (authenticationState) {
                            case UNREGISTERED -> {
                                switch (client.processInUnregistered(this)){
                                    case REFILL -> {return;}
                                    case DONE -> {continue;}
                                    case ERROR -> {
                                        silentlyClose();
                                        return;
                                    }
                                }
                            }
                            case LOGGED -> {
                                switch (client.processInLogged(this)){
                                    case REFILL -> {return;}
                                    case DONE -> {continue;}
                                    case ERROR -> {
                                        silentlyClose();
                                        return;
                                    }
                                }
                            }
                        }
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
            queueCommand.addLast(cmd);
            processOut();
            updateInterestOps();
        }

        /**
         * Assure currentCommand is set
         */
        private ProcessStatus assureCurrentCommandSet() {
            if (readingState == Context.ReadingState.WAITING_OPCODE) {

                //System.out.println("assureCurrentCommandSet....");
                switch (opcodeReader.process(bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;}
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        currentCommand = opcodeReader.get()[0];
                        opcodeReader.reset();
                        readingState = Context.ReadingState.PROCESS_IN;
                        return ProcessStatus.DONE;
                    }
                }
            }
            return ProcessStatus.DONE;
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            //System.out.println("processOut");
            while (!queueCommand.isEmpty() && bufferOut.hasRemaining()) {
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
            key.interestOps(SelectionKey.OP_READ); // OP_WRITE to send the LoginAnonymous command ?
        }

        private boolean isLogged() {
            return authenticationState == AuthenticationState.LOGGED;
        }

        enum ReadingState {
            WAITING_OPCODE,
            PROCESS_IN
        }

        private enum AuthenticationState {
            UNREGISTERED, LOGGED
        }
    }

    private static class FileSender {
        private static final Deque<FileSendInfo> fileQueue = new ArrayDeque<>(10);
        private static ClientChatFusion client;
        private final static int TIMEOUT_STD = 100;
        private final static int MAX_SLEEP_TIME = 3000;
        private final static int COMMAND_QUEUE_FULL_TIMEOUT = 200;

        // methods:
        public boolean sendNewFile(FileSendInfo fileSendInfo){
            synchronized (fileQueue){
                return fileQueue.offer(fileSendInfo);
            }
        }

        private static void senderAction() {
            int sleepTime = TIMEOUT_STD;
            for(;;){
                try {
                        if (fileQueue.isEmpty() && sleepTime < MAX_SLEEP_TIME) {
                            sleepTime += sleepTime * 2;
                        }
                        if (!fileQueue.isEmpty()) {
                            sleepTime = TIMEOUT_STD;
                            var fileInfo = fileQueue.peekFirst();
                            client.uniqueContext.queueCommand(fileInfo.buildFileChunk(client));
                            if(fileInfo.isFullySent()){
                                fileQueue.removeFirst();
                            }
                            client.selector.wakeup();
                        }
                    } catch (IOException ioe){
                    throw new UncheckedIOException(ioe);
                }
                try {
                    Thread.sleep(sleepTime);
                }catch (InterruptedException ie){
                    return;
                }
            }
        }

        public FileSender(ClientChatFusion clientInput)throws IOException{
            client = clientInput;
            try {
                new Thread(FileSender::senderAction).start();
            }catch (UncheckedIOException uioe){
                throw uioe.getCause();
            }
        }
    }

    public static class FileSendInfo {
        private final FileChannel file;
        private final String loginDst;
        private final String fileName;
        private final String serverDst;
        private static final int MAX_IN_COMMAND = 5000;
        private final int nbChunk;
        private int nbChunkSent;
        private ByteBuffer chunk = ByteBuffer.allocate(MAX_IN_COMMAND);
        public FileSendInfo(String filePath, String loginDst, String serverDst, String fileName) throws IOException {
            Path path = Path.of(filePath);
            this.file  = FileChannel.open(path);
            this.nbChunk = (Math.toIntExact(file.size()) / MAX_IN_COMMAND) +1;
            this.fileName = fileName;
            this.loginDst = loginDst;
            this.serverDst = serverDst;
            this.nbChunkSent = 0;
        }

        public boolean isFullySent() throws IOException {
            if(nbChunk == nbChunkSent){
                file.close();
                System.out.println("Just finished sending a file");
            }
            return nbChunkSent == nbChunk;
        }

        public ByteBuffer buildFileChunk(ClientChatFusion client) throws IOException {
            chunk.clear();
            var readValue = 1;
            while(chunk.hasRemaining()){
                var nbBytesRead = file.read(chunk);
                System.out.println("nbBytesRead : " + nbBytesRead);
                if(nbBytesRead <= 0){
                    break;
                }
            }
            nbChunkSent++;
            chunk.flip(); // -> Sets position & limit in order to have an exact array and not full of trailing 0.
            var data = chunk.array();
            System.out.println("data arrya size = " + data.length);
            chunk.flip();
            return client.buildFileChunk(data, serverDst, loginDst, fileName, nbChunk);
        }
    }
}