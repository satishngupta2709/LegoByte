package org.example.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * A simplified Redis-like Intset implementation.
 * It stores integers in a memory-efficient way using 16, 32, or 64-bit encoding.
 */
public class Intset {
    public static final int ENCODING_INT16 = 2;
    public static final int ENCODING_INT32 = 4;
    public static final int ENCODING_INT64 = 8;

    private int encoding;
    private int length;
    private byte[] contents;

    public Intset() {
        this.encoding = ENCODING_INT16;
        this.length = 0;
        this.contents = new byte[0];
    }

    public boolean add(long value) {
        int valEncoding = getEncoding(value);
        if (valEncoding > this.encoding) {
            upgradeAndAdd(value);
            return true;
        }

        int pos = findPosition(value);
        if (pos < length && getAt(pos) == value) {
            return false; // Already exists
        }

        // Resize and move
        byte[] newContents = new byte[(length + 1) * encoding];
        if (pos > 0) {
            System.arraycopy(contents, 0, newContents, 0, pos * encoding);
        }
        if (pos < length) {
            System.arraycopy(contents, pos * encoding, newContents, (pos + 1) * encoding, (length - pos) * encoding);
        }

        this.contents = newContents;
        setAt(pos, value);
        this.length++;
        return true;
    }

    public boolean remove(long value) {
        int valEncoding = getEncoding(value);
        if (valEncoding > this.encoding) {
            return false; // Can't be here
        }

        int pos = findPosition(value);
        if (pos < length && getAt(pos) == value) {
            // Found, remove it
            byte[] newContents = new byte[(length - 1) * encoding];
            if (pos > 0) {
                System.arraycopy(contents, 0, newContents, 0, pos * encoding);
            }
            if (pos < length - 1) {
                System.arraycopy(contents, (pos + 1) * encoding, newContents, pos * encoding, (length - pos - 1) * encoding);
            }
            this.contents = newContents;
            this.length--;
            return true;
        }
        return false;
    }

    public boolean contains(long value) {
        if (getEncoding(value) > this.encoding) {
            return false;
        }
        int pos = findPosition(value);
        return pos < length && getAt(pos) == value;
    }

    public int size() {
        return length;
    }

    public List<Long> getAll() {
        List<Long> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            result.add(getAt(i));
        }
        return result;
    }

    private int getEncoding(long value) {
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return ENCODING_INT16;
        } else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return ENCODING_INT32;
        } else {
            return ENCODING_INT64;
        }
    }

    private void upgradeAndAdd(long value) {
        int oldEncoding = this.encoding;
        this.encoding = getEncoding(value);
        byte[] newContents = new byte[(length + 1) * encoding];

        // Move elements from old contents to new contents with new encoding
        // Note: Redis handles this specifically, we'll just do it simply.
        // If value is negative, it goes at the beginning. If positive, at the end.
        // This is safe because all old elements are within oldEncoding range.
        boolean prepend = value < 0;
        
        for (int i = 0; i < length; i++) {
            long val = getAt(i, oldEncoding);
            setAt(prepend ? i + 1 : i, val, newContents, this.encoding);
        }

        if (prepend) {
            setAt(0, value, newContents, this.encoding);
        } else {
            setAt(length, value, newContents, this.encoding);
        }

        this.contents = newContents;
        this.length++;
    }

    private int findPosition(long value) {
        int low = 0, high = length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = getAt(mid);
            if (midVal < value) {
                low = mid + 1;
            } else if (midVal > value) {
                high = mid - 1;
            } else {
                return mid; // Key found
            }
        }
        return low; // Key not found, return insertion point
    }

    private long getAt(int pos) {
        return getAt(pos, this.encoding);
    }

    private long getAt(int pos, int enc) {
        ByteBuffer bb = ByteBuffer.wrap(contents, pos * enc, enc).order(ByteOrder.LITTLE_ENDIAN);
        if (enc == ENCODING_INT16) return bb.getShort();
        if (enc == ENCODING_INT32) return bb.getInt();
        return bb.getLong();
    }

    private void setAt(int pos, long value) {
        setAt(pos, value, this.contents, this.encoding);
    }

    private void setAt(int pos, long value, byte[] target, int enc) {
        ByteBuffer bb = ByteBuffer.wrap(target, pos * enc, enc).order(ByteOrder.LITTLE_ENDIAN);
        if (enc == ENCODING_INT16) bb.putShort((short) value);
        else if (enc == ENCODING_INT32) bb.putInt((int) value);
        else bb.putLong(value);
    }
}
