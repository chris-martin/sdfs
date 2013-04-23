package sdfs.rights;

public enum DelegationType {

    None("_"),

    Star("*");

    private final String marshaled;

    private DelegationType(String marshaled) {
        this.marshaled = marshaled;
    }

    String marshal() {
        return marshaled;
    }

}
