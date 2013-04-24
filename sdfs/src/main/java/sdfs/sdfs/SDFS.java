package sdfs.sdfs;

import sdfs.store.ByteStore;

public interface SDFS {

    @Deprecated
    PolicyStore policyStore();

    @Deprecated
    ByteStore byteStore();

}
