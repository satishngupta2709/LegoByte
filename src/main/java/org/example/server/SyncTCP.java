package org.example.server;


import org.example.core.Eval;
import org.example.model.LegoByteCmd;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.List;
import java.util.logging.Logger;

import static org.example.core.Resp.decodeArrayString;
/*
* this is currently not used in the project
* This class is used to create a synchronous TCP server.
* It is used to create a synchronous TCP server.
* It is used to create a synchronous TCP server.

*/
public class SyncTCP {
    private static int concurrent_connection=0;
    private  static final Logger logger= Logger.getLogger(SyncTCP.class.getName());
    private static volatile boolean isShuttingDown = false;
    private static volatile ServerSocket serverSocket = null;


    public static void RunSyncTCPServer(String host , int port){
        logger.info("Starting a synchronous TCP server on,"+host +":"+port);
        try {
            serverSocket = new ServerSocket(port);
            while (!isShuttingDown){
                logger.info("in while");
                final Socket clientSocket = serverSocket.accept();
                concurrent_connection+=1;
                logger.info("Client connected with address: "+clientSocket.getInetAddress() + " concurrent client connection: "+concurrent_connection);
                while (true){
                    try {
                        LegoByteCmd cmd=readCommand(clientSocket);
                        logger.info("command "+ cmd.Cmd());
                        respond(cmd,clientSocket);

                    }catch (Exception e){
                        respondError(e,clientSocket);
                        clientSocket.close();
                        concurrent_connection-=1;
                        logger.info("client disconnected , "+clientSocket.getInetAddress()+" concurrent connection: "+concurrent_connection);
                        break;
                    }
                }
            }


        }catch (IOException e){
            logger.warning("Error occure while creating Socket Server");
        }


    }

    public static LegoByteCmd   readCommand(Socket connection) throws Exception {
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int b;
        // Read until newline (0x0A)
        while ((b = inputStream.read()) != -1) {
            if (b == '\n') {
                break; // End of command
            }
            buffer.write(b);
        }

        // If connection closed before any data
        if (buffer.size() == 0 && b == -1) {
            throw new EOFException("Stream closed before command received");
        }

        byte[] actualData = buffer.toByteArray();

        List<String> tokens =decodeArrayString(actualData);


        if (tokens.isEmpty()) {
            throw new IOException("Empty command received");
        }

        String[] arg = tokens.subList(1, tokens.size()).toArray(new String[0]);
        return new LegoByteCmd(tokens.get(0).toUpperCase(), arg);
    }


    public static void respond(LegoByteCmd cmd, Socket connection) throws IOException {
        try {
            Eval.evalAndRespond(cmd,connection);
        }catch (Exception e){
            respondError(e,connection);
        }

    }

    public  static void respondError(Exception e, Socket connection) throws IOException {
        OutputStream outputStream = connection.getOutputStream();
        String err= "-"+e.getMessage()+"\r\n";
        outputStream.write(err.getBytes());
        outputStream.flush();
    }

    /**
     * Initiates graceful shutdown of the synchronous server
     */
    public static void initiateShutdown() {
        if (isShuttingDown) {
            logger.info("Shutdown already in progress...");
            return;
        }
        
        logger.info("Initiating graceful shutdown...");
        isShuttingDown = true;
        
        // Close server socket to stop accepting new connections
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.warning("Error closing server socket: " + e.getMessage());
            }
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

    public static int getConnectedClients() {
        return concurrent_connection;
    }

    public static boolean isShuttingDown() {
        return isShuttingDown;
    }
}
