package org.example.model;

public class DecodeResult<T> {
    private T value;
    private int delta;

    // Constructor
    public DecodeResult(T value, int delta) {
        this.value = value;
        this.delta = delta;
    }


    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }


    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }
}
