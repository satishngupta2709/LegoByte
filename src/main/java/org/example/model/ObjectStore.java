package org.example.model;

import java.sql.Timestamp;
import java.time.Instant;

public class ObjectStore<T> {
    private T value;
    private long expiresAt=-1;
    private ValueType type;
    private Encoding encoding;


    public ObjectStore(T value, long duration) {
        this(value, duration, null, null);
    }

    public ObjectStore(T value, long duration, ValueType type, Encoding encoding) {
        this.value = value;
        this.type = type;
        this.encoding = encoding;
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

    public void setExiresAt(long expiresAt){
        this.expiresAt=expiresAt;
    }

    public ValueType getType() {
        return type;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public void setType(ValueType type) {
        this.type = type;
    }

    public void setEncoding(Encoding encoding) {
        this.encoding = encoding;
    }

}
