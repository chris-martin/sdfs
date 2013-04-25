package sdfs.protocol;

import com.google.common.hash.HashCode;

import java.io.IOException;

public class HashMismatchException extends IOException {

    public HashMismatchException(HashCode expected, HashCode actual) {
        super("Expected hash " + expected.toString() + " does not match actual hash " + actual);
    }
}
