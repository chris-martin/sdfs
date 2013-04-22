package sdfs.store;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.joda.time.Instant;
import sdfs.*;

import java.io.File;
import java.nio.file.Path;

public class StoreImpl {

    private final Chronos chronos;
    private final Filesystem filesystem;

    public StoreImpl(
        Chronos chronos,
        Filesystem filesystem
    ) {
        this.chronos = chronos;
        this.filesystem = filesystem;
    }

    public StoreImpl fromConfig(Config config, String storeId) {
        return new StoreImpl(
            new ChronosImpl(),
            new FilesystemImpl(
                new File(config.getConfig("sdfs.store").getString(storeId)).toPath()
            )
        );
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
