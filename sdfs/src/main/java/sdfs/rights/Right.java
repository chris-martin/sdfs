package sdfs.rights;

public class Right {

    public final AccessType accessType;
    public final DelegationType delegationType;

    public Right(AccessType accessType, DelegationType delegationType) {
        this.accessType = accessType;
        this.delegationType = delegationType;
    }

    public String marshal() {
        return accessType.marshal() + "-" + delegationType.marshal();
    }

    public Right star() {
        return new Right(accessType, DelegationType.Star);
    }

    @Override
    public String toString() {
        return marshal();
    }

    public static final Right Get = new Right(AccessType.Get, DelegationType.None);
    public static final Right Put = new Right(AccessType.Put, DelegationType.None);
    public static final Right GetStar = new Right(AccessType.Get, DelegationType.Star);
    public static final Right PutStar = new Right(AccessType.Put, DelegationType.Star);

}
