package org.example.model;

import java.sql.Timestamp;
import java.time.Instant;

public class ObjectStore<T> {
    private T value;
    private long expiresAt=-1;


    public ObjectStore(T value, long duration) {
        this.value = value;
        if(duration>0){
            this.expiresAt= Instant.now().toEpochMilli()+duration;
        }
    }
    public T getValue() {
        return value;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expiresAt !=-1  && Instant.now().toEpochMilli() >= expiresAt;
    }

}
