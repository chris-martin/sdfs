package sdfs.sdfs;

public class ResourceNonexistentException extends RuntimeException {

    public ResourceNonexistentException() {
    }

    public ResourceNonexistentException(String message) {
        super(message);
    }

    public ResourceNonexistentException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourceNonexistentException(Throwable cause) {
        super(cause);
    }
}
