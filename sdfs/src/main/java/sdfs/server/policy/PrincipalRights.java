package sdfs.server.policy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.joda.time.Instant;

class PrincipalRights {

    final Config x;

    PrincipalRights(Config x) {
        this.x = x;
    }

    boolean isOwner() {

        try {
            if (x.getBoolean("owner")) {
                return true;
            }
        } catch (Exception ignored) { }

        return false;
    }

    PrincipalRights setOwner(boolean isOwner) {

        if (isOwner) {
            return new PrincipalRights(
                x.withValue(
                    "owner",
                    ConfigValueFactory.fromAnyRef(true)
                )
            );
        } else {
            return new PrincipalRights(
                x.withoutPath("owner")
            );
        }

    }

    Instant getExpiration(Right right) {

        try {
            return Instant.parse(x.getString(right.marshal()));
        } catch (Exception ignored) { }

        return null;
    }

    boolean hasDelegatedRight(Right right, Instant now) {

        Instant expiration = getExpiration(right);
        return expiration != null && expiration.isAfter(now);
    }

    boolean mayGrant(Right right, Instant now) {

        return isOwner()
            || hasDelegatedRight(right.star(), now);
    }

    boolean mayDo(AccessType accessType, Instant now) {

        return isOwner()
            || hasDelegatedRight(new Right(accessType, DelegationType.None), now)
            || hasDelegatedRight(new Right(accessType, DelegationType.Star), now);
    }

    PrincipalRights delegate(Right right, Instant expiration) {

        Instant previousExpiration = getExpiration(right);

        if (previousExpiration != null && previousExpiration.isAfter(expiration)) {
            return this;
        } else {
            return new PrincipalRights(
                x.withValue(
                    right.marshal(),
                    ConfigValueFactory.fromAnyRef(expiration.toString())
                )
            );
        }
    }

}
