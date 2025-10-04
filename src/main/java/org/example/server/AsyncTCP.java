package org.example.server;

import org.example.core.Eval;
import org.example.core.Resp;
import org.example.model.LegoByteCmd;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.logging.Logger;

import static org.example.core.Resp.decodeArrayString;

public class AsyncTCP {

    private static int concurrent_connection=0;
    private  static final Logger logger= Logger.getLogger(AsyncTCP.class.getName());

    public static void RunAsyncTCPServer(String host, int port){
        logger.info("Starting an asynchronous TCP server on,"+host +":"+port);

        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            server.configureBlocking(false);
            server.bind(new InetSocketAddress(host, port));
            server.register(selector, SelectionKey.OP_ACCEPT);

            Map<SocketChannel, ByteArrayOutputStream> channelBuffers = new HashMap<>();
            ByteBuffer readBuffer = ByteBuffer.allocate(8192);

            while (true){
                selector.select();
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while (iter.hasNext()){
                    SelectionKey key = iter.next();
                    iter.remove();
                    try {
                        if (!key.isValid()){
                            continue;
                        }
                        if (key.isAcceptable()){
                            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                            SocketChannel client = ssc.accept();
                            if (client != null){
                                client.configureBlocking(false);
                                client.register(selector, SelectionKey.OP_READ);
                                channelBuffers.put(client, new ByteArrayOutputStream());
                                concurrent_connection+=1;
                                logger.info("Client connected with address: "+ client.getRemoteAddress() + " concurrent client connection: "+ concurrent_connection);
                            }
                        } else if (key.isReadable()){
                            SocketChannel client = (SocketChannel) key.channel();
                            int read = client.read(readBuffer);
                            if (read == -1){
                                closeClient(client, channelBuffers);
                                continue;
                            }
                            if (read == 0){
                                continue;
                            }
                            readBuffer.flip();
                            ByteArrayOutputStream acc = channelBuffers.get(client);
                            if (acc == null){
                                acc = new ByteArrayOutputStream();
                                channelBuffers.put(client, acc);
                            }
                            while (readBuffer.hasRemaining()){
                                byte b = readBuffer.get();
                                acc.write(b);
//                                if (b == '\n'){
//                                    byte[] full = acc.toByteArray();
//                                    processLine(client, full, acc.size());
//                                    acc.reset();
//                                }
                            }
                            byte[] full = acc.toByteArray();
                            processLine(client, full, acc.size());
                            acc.reset();
                            readBuffer.clear();
                        }
                    } catch (Exception e){
                        if (key.channel() instanceof SocketChannel){
                            SocketChannel client = (SocketChannel) key.channel();
                            try {
                                respondError(e, client);
                            } catch (IOException ignored) {}
                            closeClient(client, channelBuffers);
                        } else {
                            logger.warning("Server error: "+ e.getMessage());
                        }
                    }
                }
            }

        } catch (IOException e){
            logger.warning("Error occurred while creating Async Socket Server: "+ e.getMessage());
        }

    }

    private static void processLine(SocketChannel client, byte[] bufferContent, int length) throws Exception {
        // Trim to the last newline-inclusive chunk
        int end = length;
        // Drop trailing CRLF if present
        int start = 0;
        int lastNewline = -1;
        for (int i = 0; i < end; i++){
            if (bufferContent[i] == '\n'){
                lastNewline = i;
            }
        }
        if (lastNewline == -1){
            throw new EOFException("Stream chunk without newline");
        }
        byte[] line = Arrays.copyOfRange(bufferContent, start, lastNewline); // exclude '\n'
        if (line.length > 0 && line[line.length-1] == '\r'){
            line = Arrays.copyOf(line, line.length-1);
        }

        List<String> tokens = decodeArrayString(line);
        if (tokens.isEmpty()){
            throw new IOException("Empty command received");
        }
        String[] args = tokens.subList(1, tokens.size()).toArray(new String[0]);
        LegoByteCmd cmd = new LegoByteCmd(tokens.get(0).toUpperCase(), args);
        logger.info("command "+ cmd.Cmd());
        respond(cmd, client);
    }

    private static void respond(LegoByteCmd cmd, SocketChannel client) throws IOException {
        try {
            byte[] responseBytes = Eval.evalToBytes(cmd);
            writeFully(client, responseBytes);
        } catch (Exception e){
            respondError(e, client);
        }
    }

    private static void respondError(Exception e, SocketChannel client) throws IOException {
        String err= "-"+ e.getMessage()+"\r\n";
        writeFully(client, err.getBytes());
    }

    private static void writeFully(SocketChannel channel, byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data);
        while (buf.hasRemaining()){
            int written = channel.write(buf);
            if (written == 0){
                // Yield briefly to avoid tight loop; selector will wake on next readiness
                try { Thread.yield(); } catch (Exception ignored) {}
            }
        }
    }

    private static void closeClient(SocketChannel client, Map<SocketChannel, ByteArrayOutputStream> channelBuffers){
        try {
            channelBuffers.remove(client);
            client.close();
        } catch (IOException ignored) {}
        concurrent_connection-=1;
        try {
            logger.info("client disconnected , "+ client.getRemoteAddress()+" concurrent connection: "+ concurrent_connection);
        } catch (IOException ignored) {}
    }
}
