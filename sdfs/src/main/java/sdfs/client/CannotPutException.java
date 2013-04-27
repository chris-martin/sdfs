package sdfs.client;

public class CannotPutException extends RuntimeException {

    public CannotPutException(String message) {
        super(message);
    }

    public CannotPutException(String message, Throwable cause) {
        super(message, cause);
    }
}
