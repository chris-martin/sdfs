package sdfs;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import org.joda.time.Instant;

import java.io.File;
import java.nio.file.Path;

public class Store {

    private final Chronos chronos;
    private final Filesystem filesystem;

    public Store(
        Chronos chronos,
        Filesystem filesystem
    ) {
        this.chronos = chronos;
        this.filesystem = filesystem;
    }

    public Store fromConfig(Config config, String storeId) {
        return new Store(
            new ChronosImpl(),
            new FilesystemImpl(
                new File(config.getConfig("sdfs.store").getString(storeId)).toPath()
            )
        );
    }

    private static class Policy {

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

    private Path policyFile(String resourceName) {
        return new File(resourceName).toPath().resolve("policy");
    }

    private Policy loadPolicy(String resourceName) {
        String configString = filesystem.read(policyFile(resourceName));
        if (configString == null) {
            configString = "";
        }
        return new Policy(ConfigFactory.parseString(configString));
    }

    private void savePolicy(String resourceName, Policy policy) {
        filesystem.write(
            policyFile(resourceName),
            policy.x.root().render(
                ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
            )
        );
    }

    private static class PrincipalRights {

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

    public static class Right {

        public final AccessType accessType;
        public final DelegationType delegationType;

        public Right(AccessType accessType, DelegationType delegationType) {
            this.accessType = accessType;
            this.delegationType = delegationType;
        }

        private String marshal() {
            return accessType.marshal() + "-" + delegationType.marshal();
        }

        private Right star() {
            return new Right(accessType, DelegationType.Star);
        }

        public static final Right Get = new Right(AccessType.Get, DelegationType.None);
        public static final Right Put = new Right(AccessType.Put, DelegationType.None);
        public static final Right GetStar = new Right(AccessType.Get, DelegationType.Star);
        public static final Right PutStar = new Right(AccessType.Put, DelegationType.Star);

    }

    public enum AccessType {

        Get("get"),
        Put("put");

        private final String marshaled;

        private AccessType(String marshaled) {
            this.marshaled = marshaled;
        }

        private String marshal() {
            return marshaled;
        }

    }

    public enum DelegationType {

        None("_"),

        Star("*");

        private final String marshaled;

        private DelegationType(String marshaled) {
            this.marshaled = marshaled;
        }

        private String marshal() {
            return marshaled;
        }

    }

    public synchronized boolean hasAccess(CN cn, String resourceName, AccessType accessType) {
        Policy policy = loadPolicy(resourceName);
        return policy.principalRights(cn).mayDo(accessType, chronos.now());
    }

    public synchronized void grantOwner(CN cn, String resourceName) {
        Policy policy = loadPolicy(resourceName);
        policy = policy.grantOwner(cn);
        savePolicy(resourceName, policy);
    }

    public synchronized void delegate(CN from, CN to, String resourceName, Right right, Instant expiration) {
        Policy policy = loadPolicy(resourceName);
        policy = policy.delegate(from, to, right, expiration, chronos.now());
        savePolicy(resourceName, policy);
    }

}
