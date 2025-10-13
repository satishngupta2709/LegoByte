package org.example.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a transaction context for a client connection
 * Similar to Redis MULTI/EXEC/DISCARD behavior
 */
public class Transaction {
    private boolean inTransaction;
    private List<LegoByteCmd> queuedCommands;
    
    public Transaction() {
        this.inTransaction = false;
        this.queuedCommands = new ArrayList<>();
    }
    
    /**
     * Start a new transaction (MULTI command)
     */
    public void begin() {
        this.inTransaction = true;
        this.queuedCommands.clear();
    }
    
    /**
     * Add a command to the transaction queue
     */
    public void queueCommand(LegoByteCmd cmd) {
        if (inTransaction) {
            queuedCommands.add(cmd);
        }
    }
    
    /**
     * Get all queued commands and end transaction (EXEC command)
     */
    public List<LegoByteCmd> exec() {
        List<LegoByteCmd> commands = new ArrayList<>(queuedCommands);
        this.inTransaction = false;
        this.queuedCommands.clear();
        return commands;
    }
    
    /**
     * Discard all queued commands and end transaction (DISCARD command)
     */
    public void discard() {
        this.inTransaction = false;
        this.queuedCommands.clear();
    }
    
    /**
     * Check if currently in a transaction
     */
    public boolean isInTransaction() {
        return inTransaction;
    }
    
    /**
     * Get number of queued commands
     */
    public int getQueuedCount() {
        return queuedCommands.size();
    }
}


