package org.example;

import org.example.core.AOF;
import org.example.core.Resp;
import org.example.server.AsyncTCP;
import org.example.server.SyncTCP;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

public class Main {
    private static String host="0.0.0.0";
    private static int port =2709;
    private  static final Logger logger= Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {


        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            System.out.println("Windows detected");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            System.out.println("Linux/Unix detected");
        } else if (os.contains("mac")) {
            System.out.println("macOS detected");
        } else {
            System.out.println("Unknown OS: " + os);
        }

        // Register shutdown hook for graceful shutdown
        registerShutdownHook();

        setupFlags(args);
        logger.info("Building the lego ...");
        AsyncTCP.RunAsyncTCPServer(host,port);
    }


    private static void setupFlags(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        host = args[++i];
                    } else {
                        logger.warning("No value provided for --host, using default: " + host);
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            logger.warning("Invalid port number, using default: " + port);
                        }
                    } else {
                        logger.warning("No value provided for --port, using default: " + port);
                    }
                    break;
                default:
                    logger.warning("Unknown argument: " + args[i]);
            }
        }
    }

    /**
     * Registers a shutdown hook to handle graceful shutdown on SIGTERM/SIGINT
     * Similar to Redis shutdown behavior
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Received shutdown signal (SIGTERM/SIGINT)");
            
            // Step 1: Stop accepting new connections
            AsyncTCP.initiateShutdown();
            
            // Step 2: Wait for active connections to finish (with timeout)
            int shutdownTimeoutSeconds = 10; // Similar to Redis default
            AsyncTCP.waitForConnectionsToFinish(shutdownTimeoutSeconds);
            
            // Step 3: Persist data to disk (AOF dump)
            logger.info("Saving data to disk...");
            try {
                AOF.dumpAllAOF();
                logger.info("AOF dump completed successfully.");
            } catch (Exception e) {
                logger.severe("Failed to dump AOF during shutdown: " + e.getMessage());
            }
            
            // Step 4: Final cleanup
            logger.info("LegoByte server shutdown complete. Goodbye!");
            
        }, "shutdown-hook-thread"));
        
        logger.info("Shutdown hook registered successfully.");
    }
}