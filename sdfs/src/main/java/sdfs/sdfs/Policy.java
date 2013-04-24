package sdfs.sdfs;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.joda.time.Instant;
import sdfs.rights.AccessControlException;
import sdfs.CN;
import sdfs.rights.Right;

class Policy {

    final Config x;

    Policy(Config x) {
        this.x = x;
    }

    PrincipalRights principalRights(CN cn) {
        try {
            return new PrincipalRights(x.getConfig(cn.name));
        } catch (Exception e) {
            return new PrincipalRights(ConfigFactory.empty());
        }
    }

    Policy grantOwner(CN cn) {
        return new Policy(
            x.withValue(
                cn.name,
                principalRights(cn).setOwner(true).x.root()
            )
        );
    }

    Policy delegate(CN from, CN to, Right right, Instant expiration, Instant now) {
        if (!principalRights(from).mayGrant(right, now)) {
            throw new AccessControlException();
        }
        return new Policy(
            x.withValue(
                to.name,
                principalRights(to).delegate(right, expiration).x.root()
            )
        );
    }

}
