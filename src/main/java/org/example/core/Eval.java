package org.example.core;

import org.example.model.LegoByteCmd;
import org.example.model.ObjectStore;
import org.example.model.ValueType;
import org.example.model.Encoding;
import org.example.model.Transaction;
import org.example.server.AsyncTCP;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import static org.example.core.Resp.encode;

public class Eval {
    private static final Logger logger= Logger.getLogger(Eval.class.getName());
    public static void evalAndRespond(LegoByteCmd cmd, Socket connection) throws Exception {
        switch (cmd.Cmd()) {
            case "PING":
                evalPING(cmd.Args(), connection);
                break;
            default:
                evalPING(cmd.Args(), connection); // Default to PING for unknown commands
                break;
        }
    }

    public static void evalPING(String[] args,Socket connection) throws Exception{
        byte[] b;
        if(args.length>=2){
            throw new Exception("ERR wrong number of arguments for 'ping' command");
        }
        if(args.length==0){
            b=encode("PONG",true);
        }
        else {
            b=encode(args[0],false);
        }

        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(b);
        outputStream.flush();
    }

    public static byte[] evalToBytes(List<LegoByteCmd> cmds) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (LegoByteCmd cmd : cmds) {
            byte[] result;

            switch (cmd.Cmd()) {
                case "PING" -> result = evalPingToBytes(cmd.Args());
                case "SET" -> result = evalSETToBytes(cmd.Args());
                case "GET" -> result = evalGETToBytes(cmd.Args());
                case "TTL" -> result = evalTTLToByte(cmd.Args());
                case "DEL" -> result = evalDELToByte(cmd.Args());
                case "EXPIRE" -> result = evalEXPIREToByte(cmd.Args());
                case "BGREWRITEAOF" -> result = evalBGREWRITEAOF(cmd.Args());
                case "INCRBY" -> result = evalINCR(cmd.Args());
                case "INFO" -> result = evalINFO(cmd.Args());
                case "CLIENT" -> result = evalCLIENT(cmd.Args());
                case "LATENCY" -> result = evalLATENCY(cmd.Args());
                case "SHUTDOWN" -> result = evalSHUTDOWN(cmd.Args());
                default -> result = evalPingToBytes(cmd.Args());
            }

            if (result != null) {
                output.write(result);
            }
        }

        return output.toByteArray();

    }

    /**
     * Evaluates commands with transaction support
     * Similar to Redis MULTI/EXEC/DISCARD behavior
     */
    public static byte[] evalToBytes(List<LegoByteCmd> cmds, Transaction transaction) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (LegoByteCmd cmd : cmds) {
            byte[] result;
            String cmdName = cmd.Cmd();

            // Transaction control commands are always executed immediately
            switch (cmdName) {
                case "MULTI" -> {
                    result = evalMULTI(transaction);
                    if (result != null) {
                        output.write(result);
                    }
                    continue;
                }
                case "EXEC" -> {
                    result = evalEXEC(transaction);
                    if (result != null) {
                        output.write(result);
                    }
                    continue;
                }
                case "DISCARD" -> {
                    result = evalDISCARD(transaction);
                    if (result != null) {
                        output.write(result);
                    }
                    continue;
                }
            }

            // If in transaction, queue the command instead of executing
            if (transaction.isInTransaction()) {
                transaction.queueCommand(cmd);
                // Redis responds with "QUEUED" for each command in a transaction
                output.write(Resp.encode("QUEUED", true));
                continue;
            }

            // Not in transaction, execute normally
            result = evalCommand(cmd);
            if (result != null) {
                output.write(result);
            }
        }

        return output.toByteArray();
    }

    /**
     * Evaluates a single command
     */
    private static byte[] evalCommand(LegoByteCmd cmd) throws Exception {
        return switch (cmd.Cmd()) {
            case "PING" -> evalPingToBytes(cmd.Args());
            case "SET" -> evalSETToBytes(cmd.Args());
            case "GET" -> evalGETToBytes(cmd.Args());
            case "TTL" -> evalTTLToByte(cmd.Args());
            case "DEL" -> evalDELToByte(cmd.Args());
            case "EXPIRE" -> evalEXPIREToByte(cmd.Args());
            case "BGREWRITEAOF" -> evalBGREWRITEAOF(cmd.Args());
            case "INCRBY" -> evalINCR(cmd.Args());
            case "INFO" -> evalINFO(cmd.Args());
            case "CLIENT" -> evalCLIENT(cmd.Args());
            case "LATENCY" -> evalLATENCY(cmd.Args());
            case "SHUTDOWN" -> evalSHUTDOWN(cmd.Args());
            default -> evalPingToBytes(cmd.Args());
        };
    }



    private static byte[] evalINCR(String[] args) throws Exception{
        if(args.length<1){
            return Resp.encode("ERR wrong number of arguments for 'incr' command",false);
        }
        String key = args[0];
        ObjectStore<String> obj = Store.Get(key);
        if(obj==null){
            // Create with 0, no expiry
            obj = new ObjectStore<>("0", -1, ValueType.STRING, Encoding.INT);
            Store.Put(key, obj);
        }

        String current = obj.getValue();
        if (current == null) {
            current = "0";
        }
        long num;
        try {
            num = Long.parseLong(current);
        } catch (NumberFormatException e) {
            return  Resp.encode("ERR value is not an integer or out of range",false);
        }
        num += 1L;
        obj.setValue(Long.toString(num));
        obj.setType(ValueType.STRING);
        obj.setEncoding(Encoding.INT);
        return Resp.encode((int)num,false);

    }

    // TODO: Make it async by forking a new process
    private static byte[] evalBGREWRITEAOF(String[] args) throws Exception{
        AOF.dumpAllAOF();
        return Resp.encode("OK",false);
    }

    private static byte[] evalEXPIREToByte(String[] args) throws Exception {
        if(args.length<=1){
            throw new Exception("(error) ERR wrong number of arguments for 'expire' command");
        }
        String key= args[0];
        int exDurationSec=0;
        try{
            exDurationSec= Integer.parseInt(args[1]);
        }catch (Exception e){
            throw new Exception("(error) ERR value is not an integer or out of range");
        }
        ObjectStore obj = Store.Get(key);
        if (obj==null){
            return "0".getBytes();
        }
        long expireAt= obj.getExpiresAt()+exDurationSec* 1000L;

        obj.setExiresAt(expireAt);
        return "1".getBytes();
    }

    private static byte[] evalDELToByte(String[] args) {
        int countDeleted=0;
        for(int i =0;i<args.length;i++){
            Store.Del(args[i]);
            countDeleted++;
        }
        return Resp.encode(countDeleted,false);
    }

    private static byte[] evalTTLToByte(String[] args) throws Exception {
        if(args.length!=1){
            throw new Exception("ERR wrong number of arguments for 'ttl' command");
        }
        String key = args[0];
        ObjectStore obj = Store.Get(key);

        if (obj == null){
            return Resp.encode("Nil",false);
        }
        if(obj.getExpiresAt()==-1){
            return Resp.encode("-1",false);
        }
        long durationMs= obj.getExpiresAt()- Instant.now().toEpochMilli();

        if(durationMs<0){
            return  Resp.encode("-2",false);
        }
        String d= String.valueOf(durationMs/1000);
        return Resp.encode(d,false);
    }

    private static byte[] evalGETToBytes(String[] args)  throws  Exception{
        if(args.length!=1){
            throw new Exception("ERR wrong number of arguments for 'get' command");
        }
        String key = args[0];
        ObjectStore data = Store.Get(key);
        if (data == null){
            return Resp.encode("Nil",false);

        }
        if (data.isExpired()){
            return  Resp.encode("Nil",false);
        }

        return Resp.encode(data.getValue(),false);
    }

    private static byte[] evalSETToBytes(String[] args) throws Exception {
        if(args.length <=1){
            return Resp.encode("ERR wrong number of arguments for 'set' command",false);
        }
        String key= args[0];
        String value =args[1];
        int exDurationMs=-1;

        for(int i=2;i<args.length;i++){
            String arg=args[i];
            switch (arg){
                case "EX":
                case "ex":
                  i++;
                  if(i==args.length){
                      return Resp.encode("ERR syntax error",false);
                  }
                  try {
                      exDurationMs = Integer.parseInt(args[3]);
                  }catch (Exception e){
                      throw e;
                  }
                  exDurationMs=exDurationMs*1000;
                  break;
                default:
                    return Resp.encode("ERR syntax error",false);

            }
        }
        // Determine internal encoding for string value
        Encoding encoding = Encoding.RAW;
        if (value != null) {
            try {
                // Detect if the provided value is an integer. We do not change client-visible type,
                // only the internal encoding metadata.
                Long.parseLong(value);
                encoding = Encoding.INT;
            } catch (NumberFormatException ignored) {
                encoding = Encoding.RAW;
            }
        }

        var data = new ObjectStore<String>(value, exDurationMs, ValueType.STRING, encoding);
        Store.Put(key,data);
        return Resp.encode("OK",false);
    }

    public static byte[] evalPingToBytes(String[] args) throws Exception {
        if (args.length >= 2){
            //throw new Exception("err");
            return Resp.encode("ERR wrong number of arguments for 'ping' command",false);
        }
        if (args.length == 0){
            return Resp.encode("PONG", true);
        } else {
            return Resp.encode(args[0], false);
        }
    }

    private static byte[] evalINFO(String[] args) {
        StringBuilder info = new StringBuilder();
        info.append("# Server\n");
        info.append("legoByte_version:0.0.1\n");
        info.append("os:").append(System.getProperty("os.name")).append("\n");
        info.append("arch_bits:").append(System.getProperty("os.arch").contains("64") ? "64" : "32").append("\n");
        info.append("process_id:").append(ProcessHandle.current().pid()).append("\n");

        info.append("\n# Memory\n");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        info.append("used_memory:").append(usedMemory).append("\n");
        info.append("used_memory_human:").append(formatBytes(usedMemory)).append("\n");
        info.append("total_system_memory:").append(runtime.maxMemory()).append("\n");

        info.append("\n# Stats\n");
        info.append("total_connections_received:0\n");
        info.append("total_commands_processed:0\n");
        info.append("instantaneous_ops_per_sec:0\n");
        info.append("connected_clients:").append(org.example.server.AsyncTCP.getConnectedClients()).append("\n");
        
        info.append("\n# Keyspace\n");
        info.append("db0:keys=").append(Store.snapshot().size()).append(",expires=0,avg_ttl=0\n");
        
        return Resp.encode(info.toString(), false);
    }

    private static byte[] evalCLIENT(String[] args) throws Exception {
        if (args.length < 1) {
            return Resp.encode("ERR wrong number of arguments for 'client' command", false);
        }
        
        String subcommand = args[0].toUpperCase();
        switch (subcommand) {
            case "LIST":
                return evalCLIENT_LIST(args);
            case "INFO":
                return evalCLIENT_INFO(args);
            case "SETNAME":
                return evalCLIENT_SETNAME(args);
            case "GETNAME":
                return evalCLIENT_GETNAME(args);
            default:
                return Resp.encode("ERR unknown subcommand '" + subcommand + "'", false);
        }
    }

    private static byte[] evalCLIENT_LIST(String[] args) throws Exception {
        // Simple client list - in a real implementation, you'd track active connections
        StringBuilder clients = new StringBuilder();
        clients.append("id=1 addr=127.0.0.1:12345 fd=8 name= age=0 idle=0 flags=N db=0 sub=0 psub=0 multi=-1 qbuf=0 qbuf-free=0 obl=0 oll=0 omem=0 events=r cmd=client\n");
        return Resp.encode(clients.toString(), false);
    }

    private static byte[] evalCLIENT_INFO(String[] args) throws Exception {
        StringBuilder info = new StringBuilder();
        info.append("id=1\n");
        info.append("addr=127.0.0.1:12345\n");
        info.append("fd=8\n");
        info.append("name=\n");
        info.append("age=0\n");
        info.append("idle=0\n");
        info.append("flags=N\n");
        info.append("db=0\n");
        info.append("sub=0\n");
        info.append("psub=0\n");
        info.append("multi=-1\n");
        info.append("qbuf=0\n");
        info.append("qbuf-free=0\n");
        info.append("obl=0\n");
        info.append("oll=0\n");
        info.append("omem=0\n");
        info.append("events=r\n");
        info.append("cmd=client\n");
        return Resp.encode(info.toString(), false);
    }

    private static byte[] evalCLIENT_SETNAME(String[] args) throws Exception {
        if (args.length < 2) {
            return Resp.encode("ERR wrong number of arguments for 'client setname' command", false);
        }
        // In a real implementation, you'd store the client name
        return Resp.encode("OK", false);
    }

    private static byte[] evalCLIENT_GETNAME(String[] args) throws Exception {
        // In a real implementation, you'd return the actual client name
        return Resp.encode("", false); // Empty string means no name set
    }

    private static byte[] evalLATENCY(String[] args) throws Exception {
        if (args.length < 1) {
            return Resp.encode("ERR wrong number of arguments for 'latency' command", false);
        }
        
        String subcommand = args[0].toUpperCase();
        switch (subcommand) {
            case "LATEST":
                return evalLATENCY_LATEST(args);
            case "HISTORY":
                return evalLATENCY_HISTORY(args);
            case "RESET":
                return evalLATENCY_RESET(args);
            case "DOCTOR":
                return evalLATENCY_DOCTOR(args);
            default:
                return Resp.encode("ERR unknown subcommand '" + subcommand + "'", false);
        }
    }

    private static byte[] evalLATENCY_LATEST(String[] args) throws Exception {
        // Return empty array - no latency events recorded
        return Resp.encode("", false);
    }

    private static byte[] evalLATENCY_HISTORY(String[] args) throws Exception {
        if (args.length < 2) {
            return Resp.encode("ERR wrong number of arguments for 'latency history' command", false);
        }
        // Return empty array - no latency history
        return Resp.encode("", false);
    }

    private static byte[] evalLATENCY_RESET(String[] args) throws Exception {
        // Reset latency tracking (no-op in this implementation)
        return Resp.encode("OK", false);
    }

    private static byte[] evalLATENCY_DOCTOR(String[] args) throws Exception {
        StringBuilder doctor = new StringBuilder();
        doctor.append("I'm sorry, I can't help you with latency issues. ");
        doctor.append("I am just a simple LegoByte server and don't have advanced latency monitoring capabilities.");
        return Resp.encode(doctor.toString(), false);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fK", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fM", bytes / (1024.0 * 1024.0));
        return String.format("%.1fG", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Evaluates the SHUTDOWN command
     * Similar to Redis SHUTDOWN behavior
     * Options: NOSAVE (skip saving), SAVE (force save)
     */
    private static byte[] evalSHUTDOWN(String[] args) throws Exception {
        boolean save = true; // Default: save before shutdown
        
        // Parse optional arguments
        if (args.length > 0) {
            String option = args[0].toUpperCase();
            if (option.equals("NOSAVE")) {
                save = false;
            } else if (option.equals("SAVE")) {
                save = true;
            } else {
                return Resp.encode("ERR Invalid SHUTDOWN option. Use SAVE or NOSAVE", false);
            }
        }
        
        // Initiate shutdown in a separate thread to avoid blocking the response
        final boolean shouldSave = save;
        new Thread(() -> {
            try {
                //Thread.sleep(100); // Small delay to allow response to be sent
                
                if (shouldSave) {
                    logger.info("Saving data before shutdown...");
                    try {
                        AOF.dumpAllAOF();
                        logger.info("AOF dump completed successfully.");
                    } catch (Exception e) {
                        logger.info("Failed to dump AOF: " + e.getMessage());
                    }
                }
                
                // Trigger graceful shutdown
                AsyncTCP.initiateShutdown();
                AsyncTCP.waitForConnectionsToFinish(10);
                
                logger.info("Shutdown complete. Exiting...");
                System.exit(0);
            } catch (Exception e) {
                logger.info("Error during shutdown: " + e.getMessage());
            }
        }, "shutdown-command-thread").start();
        
        // Redis SHUTDOWN does not send a response - just closes the connection
        // Returning empty byte array signals no response
        return new byte[0];
    }

    /**
     * MULTI - Start a transaction
     * Returns OK if successful
     */
    private static byte[] evalMULTI(Transaction transaction) throws Exception {
        if (transaction.isInTransaction()) {
            return Resp.encode("ERR MULTI calls can not be nested", false);
        }
        
        transaction.begin();
        logger.info("Transaction started. Commands will be queued.");
        return Resp.encode("OK", true);
    }

    /**
     * EXEC - Execute all queued commands in the transaction
     * Returns array of results from each command
     */
    private static byte[] evalEXEC(Transaction transaction) throws Exception {
        if (!transaction.isInTransaction()) {
            return Resp.encode("ERR EXEC without MULTI", false);
        }

        List<LegoByteCmd> queuedCommands = transaction.exec();
        logger.info("Executing transaction with " + queuedCommands.size() + " queued commands.");
        
        // Execute all queued commands and collect results
        ByteArrayOutputStream results = new ByteArrayOutputStream();
        
        // Redis returns an array of results
        // Format: *<count>\r\n followed by each result
        String arrayHeader = "*" + queuedCommands.size() + "\r\n";
        results.write(arrayHeader.getBytes());
        
        for (LegoByteCmd cmd : queuedCommands) {
            try {
                byte[] result = evalCommand(cmd);
                if (result != null) {
                    results.write(result);
                } else {
                    // If no result, send nil
                    results.write("$-1\r\n".getBytes());
                }
            } catch (Exception e) {
                // Redis doesn't rollback on errors, it returns the error for that command
                String error = "-" + e.getMessage() + "\r\n";
                results.write(error.getBytes());
                logger.warning("Error executing command in transaction: " + cmd.Cmd() + " - " + e.getMessage());
            }
        }
        
        return results.toByteArray();
    }

    /**
     * DISCARD - Discard all queued commands and exit transaction
     */
    private static byte[] evalDISCARD(Transaction transaction) throws Exception {
        if (!transaction.isInTransaction()) {
            return Resp.encode("ERR DISCARD without MULTI", false);
        }
        
        int queuedCount = transaction.getQueuedCount();
        transaction.discard();
        logger.info("Transaction discarded. " + queuedCount + " queued commands removed.");
        return Resp.encode("OK", true);
    }
}
