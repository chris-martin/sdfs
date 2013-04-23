package sdfs.server.policy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.joda.time.Instant;
import sdfs.CN;
import sdfs.filesystem.Filesystem;
import sdfs.filesystem.FilesystemImpl;
import sdfs.rights.AccessType;
import sdfs.rights.Right;
import sdfs.time.Chronos;
import sdfs.time.ChronosImpl;

import java.io.File;
import java.nio.file.Path;

public class PolicyStoreImpl implements PolicyStore {

    private final Chronos chronos;
    private final Filesystem filesystem;

    public PolicyStoreImpl(
        Chronos chronos,
        Filesystem filesystem
    ) {
        this.chronos = chronos;
        this.filesystem = filesystem;
    }

    public PolicyStoreImpl fromConfig(Config config, String storeId) {
        return new PolicyStoreImpl(
            new ChronosImpl(),
            new FilesystemImpl(
                new File(config.getConfig("sdfs.store").getString(storeId)).toPath()
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

}
