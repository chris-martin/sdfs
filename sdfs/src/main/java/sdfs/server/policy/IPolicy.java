package sdfs.server.policy;

import org.joda.time.Instant;
import sdfs.CN;

// TODO terrible name
public interface IPolicy {

    boolean hasAccess(CN cn, String resourceName, AccessType accessType);

    void grantOwner(CN cn, String resourceName);

    void delegate(CN from, CN to, String resourceName, Right right, Instant expiration);
}
