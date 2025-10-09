package org.example.core;

import org.example.model.LegoByteCmd;
import org.example.model.ObjectStore;
import org.example.model.ValueType;
import org.example.model.Encoding;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.List;

import static org.example.core.Resp.encode;

public class Eval {

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
                default -> result = evalPingToBytes(cmd.Args());
            }

            if (result != null) {
                output.write(result);
            }
        }

        return output.toByteArray();

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
}
