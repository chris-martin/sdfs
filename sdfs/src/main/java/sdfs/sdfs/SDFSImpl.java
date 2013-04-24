package sdfs.sdfs;

import com.typesafe.config.Config;
import sdfs.store.ByteStore;
import sdfs.store.FileByteStore;

import java.io.File;

public class SDFSImpl implements SDFS {

    private final FileByteStore byteStore;
    private final PolicyStore policyStore;

    public SDFSImpl(FileByteStore byteStore, PolicyStore policyStore) {
        this.byteStore = byteStore;
        this.policyStore = policyStore;
    }

    @Override
    public PolicyStore policyStore() {
        return policyStore;
    }

    @Override
    public ByteStore byteStore() {
        return byteStore;
    }

    public static SDFSImpl fromConfig(Config config) {
        return new SDFSImpl(
            new FileByteStore(new File(config.getString("sdfs.server-store-path"))),
            PolicyStoreImpl.fromConfig(config)
        );
    }

}
