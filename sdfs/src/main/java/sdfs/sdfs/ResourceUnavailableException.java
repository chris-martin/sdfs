package sdfs.sdfs;

/**
 * Resource is locked and temporarily unavailable.
 */
public class ResourceUnavailableException extends RuntimeException {

    public ResourceUnavailableException() {
    }

    public ResourceUnavailableException(String message) {
        super(message);
    }

    public ResourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourceUnavailableException(Throwable cause) {
        super(cause);
    }

}
