package sdfs.sdfs;

public class AccessControlException extends RuntimeException {

    public AccessControlException() {
    }

    public AccessControlException(String message) {
        super(message);
    }

    public AccessControlException(String message, Throwable cause) {
        super(message, cause);
    }

    public AccessControlException(Throwable cause) {
        super(cause);
    }


}
