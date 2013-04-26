package sdfs.sdfs;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.typesafe.config.Config;
import org.joda.time.Instant;
import sdfs.CN;
import sdfs.store.ByteStore;
import sdfs.store.FileStore;
import sdfs.store.PathManipulator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SDFSImpl implements SDFS {

    private final ByteStore byteStore;
    private final PathManipulator pathManipulator;
    private final PolicyStore policyStore;

    public SDFSImpl(ByteStore byteStore, PathManipulator pathManipulator, PolicyStore policyStore) {
        this.byteStore = byteStore;
        this.pathManipulator = pathManipulator;
        this.policyStore = policyStore;
    }

    public static SDFSImpl fromConfig(Config config) {
        FileStore fileStore = new FileStore(new File(config.getString("sdfs.store.server")).toPath());
        return new SDFSImpl(fileStore, fileStore, PolicyStoreImpl.fromConfig(config));
    }

    private static class Lock {

        final Set<Get> gets = new HashSet<>();

        Put put;

    }

    private final Map<String, Lock> locks = new HashMap<>();

    private Lock getOrCreateLock(String resourceName) {

        Lock lock = locks.get(resourceName);

        if (lock == null) {
            lock = new Lock();
            locks.put(resourceName, lock);
        }

        return lock;
    }

    public synchronized Get get(CN cn, String resourceName) {

        Lock lock = getOrCreateLock(resourceName);

        if (lock.put != null) {
            throw new ResourceUnavailableException("Cannot read; file is currently being written");
        }

        if (!pathManipulator.exists(resourcePath(resourceName))) {
            throw new ResourceNonexistentException();
        }

        if (!policyStore.hasAccess(cn, resourceName, AccessType.Get)) {
            throw new AccessControlException();
        }

        Get get = new GetImpl(resourceName);
        lock.gets.add(get);
        return get;
    }

    public synchronized Put put(CN cn, String resourceName) {

        Lock lock = getOrCreateLock(resourceName);

        if (lock.put != null) {
            throw new ResourceUnavailableException("Cannot write; file is currently being written");
        }

        if (!lock.gets.isEmpty()) {
            throw new ResourceUnavailableException("Cannot write; file is currently being read");
        }

        if (!pathManipulator.exists(resourcePath(resourceName).resolve("meta"))) {
            policyStore.grantOwner(cn, resourceName);
        }

        if (!policyStore.hasAccess(cn, resourceName, AccessType.Put)) {
            throw new AccessControlException();
        }

        Put put = new PutImpl(resourceName);
        lock.put = put;
        return put;
    }

    public synchronized void delegate(CN from, CN to, String resourceName, Right right, Instant expiration) {

        if (!pathManipulator.exists(resourcePath(resourceName))) {
            throw new ResourceNonexistentException();
        }

        policyStore.delegate(from, to, resourceName, right, expiration);

    }

    private synchronized void release(GetImpl get) {

        Lock lock = locks.get(get.resourceName);
        lock.gets.remove(get);

        if (lock.gets.isEmpty()) {
            locks.remove(get.resourceName);
        }
    }

    private synchronized void release(PutImpl put) {

        locks.remove(put.resourceName);
    }

    private Path resourcePath(String resourceName) {
        return new File(resourceName).toPath();
    }

    private abstract class Operation {

        final String resourceName;

        Operation(String resourceName) {
            this.resourceName = resourceName;
        }

        Path path() {
            return resourcePath(resourceName);
        }

    }

    private class GetImpl extends Operation implements Get {

        GetImpl(String resourceName) {
            super(resourceName);
        }

        public ByteSource contentByteSource() {
            return byteStore.get(path().resolve("content"));
        }

        public ByteSource metaByteSource() {
            return byteStore.get(path().resolve("meta"));
        }

        public void release() {
            SDFSImpl.this.release(this);
        }

    }

    private class PutImpl extends Operation implements Put {

        PutImpl(String resourceName) {
            super(resourceName);
        }

        Path tmp() {
            return path().resolve("tmp");
        }

        public ByteSink contentByteSink() throws IOException {
            return byteStore.put(tmp().resolve("content"));
        }

        public ByteSink metaByteSink() throws IOException {
            return byteStore.put(tmp().resolve("meta"));
        }

        void moveFromTmp(String filename) throws IOException {
            pathManipulator.move(tmp().resolve(filename), path().resolve(filename));
        }

        void deleteTmp(String filename) throws IOException {
            pathManipulator.delete(tmp().resolve(filename));
        }

        public void release() throws IOException {
            try {
                moveFromTmp("content");
                moveFromTmp("meta");
            } finally {
                SDFSImpl.this.release(this);
            }
        }

        @Override
        public void abort() throws IOException {
            try {
                try {
                    deleteTmp("content");
                } finally {
                    deleteTmp("meta");
                }
            } finally {
                SDFSImpl.this.release(this);
            }
        }

    }

}
