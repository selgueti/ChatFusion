package fr.uge.net.chatFusion.util;

//import fr.uge.net.chatFusion.clientChatFusion.ClientChatFusion;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSendInfo {
    private final FileChannel file;
    private final String loginDst;
    private final String fileName;
    private final String serverDst;
    private int progress = 0;
    private static final int MAX_IN_COMMAND = 5000;
    private final int nbChunk;
    private int nbChunkSent;
    private ByteBuffer chunk = ByteBuffer.allocate(MAX_IN_COMMAND);
    public FileSendInfo(String filePath, String loginDst, String serverDst, String fileName) throws IOException {
        Path path = Path.of(filePath);
        file  = FileChannel.open(path);
        nbChunk = Math.toIntExact(file.size()) / MAX_IN_COMMAND);
        this.fileName = fileName;
        this.loginDst = loginDst;
        this.serverDst = serverDst;
        this.nbChunkSent = 0;
    }

    public boolean isFullySent(){
        return nbChunkSent == nbChunk;
    }

    public ByteBuffer buildFileChunk(ClientChatFusion client) throws IOException {
        var readValue = 1;
        while(chunk.hasRemaining()){
            if(file.read(chunk) <= 0){
                break;
            }
        }
        nbChunkSent++;
        chunk.flip(); // -> Sets position & limit in order to have an exact array and not full of trailing 0.
        var data = chunk.array();
        chunk.flip();
        return client.buildFileChunk(data, serverDst, loginDst, fileName, nbChunk);
    }
}
