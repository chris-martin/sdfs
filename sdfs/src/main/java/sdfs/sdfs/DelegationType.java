package sdfs.sdfs;

public enum DelegationType {

    None("none"),

    Star("star");

    private final String marshaled;

    private DelegationType(String marshaled) {
        this.marshaled = marshaled;
    }

    String marshal() {
        return marshaled;
    }

}
