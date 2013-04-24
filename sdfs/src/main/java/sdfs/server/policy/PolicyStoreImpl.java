package sdfs.server.policy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.joda.time.Instant;
import sdfs.CN;
import sdfs.store.FileStringStore;
import sdfs.store.StringStore;
import sdfs.rights.AccessType;
import sdfs.rights.Right;
import sdfs.time.Chronos;
import sdfs.time.ChronosImpl;

import java.io.File;
import java.nio.file.Path;

public class PolicyStoreImpl implements PolicyStore {

    private final Chronos chronos;
    private final StringStore stringStore;

    public PolicyStoreImpl(
        Chronos chronos,
        StringStore stringStore
    ) {
        this.chronos = chronos;
        this.stringStore = stringStore;
    }

    public static PolicyStoreImpl fromConfig(Config config) {
        return new PolicyStoreImpl(
            new ChronosImpl(),
            new FileStringStore(
                new File(config.getString("sdfs.store.server")).toPath()
            )
        );
    }

    @Override
    public synchronized boolean hasAccess(CN cn, String resourceName, AccessType accessType) {
        Policy policy = loadPolicy(resourceName);
        return policy.principalRights(cn).mayDo(accessType, chronos.now());
    }

    @Override
    public synchronized void grantOwner(CN cn, String resourceName) {
        Policy policy = loadPolicy(resourceName);
        policy = policy.grantOwner(cn);
        savePolicy(resourceName, policy);
    }

    @Override
    public synchronized void delegate(CN from, CN to, String resourceName, Right right, Instant expiration) {
        Policy policy = loadPolicy(resourceName);
        policy = policy.delegate(from, to, right, expiration, chronos.now());
        savePolicy(resourceName, policy);
    }

    private Path policyFile(String resourceName) {
        return new File(resourceName).toPath().resolve("policy");
    }

    private Policy loadPolicy(String resourceName) {
        String configString = stringStore.read(policyFile(resourceName));
        if (configString == null) {
            configString = "";
        }
        return new Policy(ConfigFactory.parseString(configString));
    }

    private void savePolicy(String resourceName, Policy policy) {
        stringStore.write(
            policyFile(resourceName),
            policy.x.root().render(
                ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
            )
        );
    }

}
