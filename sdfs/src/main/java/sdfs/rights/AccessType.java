package sdfs.rights;

public enum AccessType {

    Get("get"),
    Put("put");

    private final String marshaled;

    private AccessType(String marshaled) {
        this.marshaled = marshaled;
    }

    String marshal() {
        return marshaled;
    }

}
