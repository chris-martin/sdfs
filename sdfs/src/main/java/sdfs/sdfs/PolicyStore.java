package sdfs.sdfs;

import org.joda.time.Instant;
import sdfs.CN;

public interface PolicyStore {

    boolean hasAccess(CN cn, String resourceName, AccessType accessType);

    void grantOwner(CN cn, String resourceName);

    void delegate(CN from, CN to, String resourceName, Right right, Instant expiration);

}
