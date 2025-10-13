package org.example.server;

import org.example.core.Eval;
import org.example.core.Expire;
import org.example.core.Resp;
import org.example.model.LegoByteCmd;
import org.example.model.Transaction;

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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static org.example.core.Resp.decode;
import static org.example.core.Resp.decodeArrayString;

public class AsyncTCP {

    private static volatile int concurrent_connection=0;
    private static Duration crnnFrequency= Duration.ofSeconds(1);
    private static Instant lastCronExecutionTime = Instant.now();
    private  static final Logger logger= Logger.getLogger(AsyncTCP.class.getName());
    private static volatile boolean isShuttingDown = false;
    private static volatile ServerSocketChannel serverChannel = null;
    private static volatile Selector mainSelector = null;

    public static void RunAsyncTCPServer(String host, int port){
        logger.info("Starting an asynchronous TCP server on,"+host +":"+port);

        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {

            serverChannel = server;
            mainSelector = selector;
            
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(host, port));
            server.register(selector, SelectionKey.OP_ACCEPT);

            Map<SocketChannel, ByteArrayOutputStream> channelBuffers = new HashMap<>();
            Map<SocketChannel, Transaction> channelTransactions = new HashMap<>();
            ByteBuffer readBuffer = ByteBuffer.allocate(8192);

            while (!isShuttingDown){

                if (Instant.now().isAfter(lastCronExecutionTime.plus(crnnFrequency))){
                    Expire.DeleteExpiredKey();
                    lastCronExecutionTime=Instant.now();
                }

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
                            // Stop accepting new connections during shutdown
                            if (isShuttingDown) {
                                continue;
                            }
                            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                            SocketChannel client = ssc.accept();
                            if (client != null){
                                client.configureBlocking(false);
                                client.register(selector, SelectionKey.OP_READ);
                                channelBuffers.put(client, new ByteArrayOutputStream());
                                channelTransactions.put(client, new Transaction());
                                concurrent_connection+=1;
                                logger.info("Client connected with address: "+ client.getRemoteAddress() + " concurrent client connection: "+ concurrent_connection);
                            }
                        } else if (key.isReadable()){
                            SocketChannel client = (SocketChannel) key.channel();
                            int read = client.read(readBuffer);
                            if (read == -1){
                                closeClient(client, channelBuffers, channelTransactions);
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
                            Transaction transaction = channelTransactions.get(client);
                            if (transaction == null) {
                                transaction = new Transaction();
                                channelTransactions.put(client, transaction);
                            }
                            processLine(client, full, acc.size(), transaction);
                            acc.reset();
                            readBuffer.clear();
                        }
                    } catch (Exception e){
                        if (key.channel() instanceof SocketChannel){
                            SocketChannel client = (SocketChannel) key.channel();
                            try {
                                respondError(e, client);
                            } catch (IOException ignored) {}
                            closeClient(client, channelBuffers, channelTransactions);
                        } else {
                            logger.warning("Server error: "+ e.getMessage());
                        }
                    }
                }
            }

        } catch (IOException e){
            logger.warning("Error occurred while creating Async Socket Server: "+ e.getMessage());
        } finally {
            logger.info("Server shutting down ...");
        }

    }

    private static void processLine(SocketChannel client, byte[] bufferContent, int length, Transaction transaction) throws Exception {
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

        List<LegoByteCmd> cmds = new ArrayList<>();
        List<Object> value = decode(line);
        for(Object a : value){
            List<String> tokens = decodeArrayString(a);
            if (tokens.isEmpty()){
                throw new IOException("Empty command received");
            }
            String[] args = tokens.subList(1, tokens.size()).toArray(new String[0]);
            LegoByteCmd cmd = new LegoByteCmd(tokens.get(0).toUpperCase(), args);
            cmds.add(cmd);
            logger.info("command "+ cmd.Cmd());
        }


        respond(cmds, client, transaction);
    }

    private static void respond(List<LegoByteCmd> cmds, SocketChannel client, Transaction transaction) throws IOException {
        try {
            byte[] responseBytes = Eval.evalToBytes(cmds, transaction);
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

    public static int getConnectedClients() {
        return concurrent_connection;
    }

    private static void closeClient(SocketChannel client, Map<SocketChannel, ByteArrayOutputStream> channelBuffers, Map<SocketChannel, Transaction> channelTransactions){
        try {
            channelBuffers.remove(client);
            channelTransactions.remove(client);
            client.close();
        } catch (IOException ignored) {}
        concurrent_connection-=1;
        try {
            logger.info("client disconnected , "+ client.getRemoteAddress()+" concurrent connection: "+ concurrent_connection);
        } catch (IOException ignored) {}
    }

    /**
     * Initiates graceful shutdown of the server
     * Similar to Redis SHUTDOWN command behavior
     */
    public static void initiateShutdown() {
        if (isShuttingDown) {
            logger.info("Shutdown already in progress...");
            return;
        }
        
        logger.info("Initiating graceful shutdown...");
        isShuttingDown = true;
        
        // Wakeup selector to process the shutdown flag
        if (mainSelector != null) {
            mainSelector.wakeup();
        }
    }

    /**
     * Waits for all active connections to finish with a timeout
     * @param timeoutSeconds maximum time to wait for connections to finish
     */
    public static void waitForConnectionsToFinish(int timeoutSeconds) {
        logger.info("Waiting for " + concurrent_connection + " active connections to finish (timeout: " + timeoutSeconds + "s)...");
        
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;
        
        while (concurrent_connection > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeoutMillis) {
                logger.warning("Timeout reached. Forcing shutdown with " + concurrent_connection + " active connections remaining.");
                break;
            }
            
            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Interrupted while waiting for connections to finish.");
                break;
            }
        }
        
        if (concurrent_connection == 0) {
            logger.info("All client connections closed successfully.");
        }
    }

    /**
     * Returns true if the server is shutting down
     */
    public static boolean isShuttingDown() {
        return isShuttingDown;
    }
}
