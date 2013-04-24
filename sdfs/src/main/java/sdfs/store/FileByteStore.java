package sdfs.store;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

public class FileByteStore implements ByteStore {

    private final File dir;

    public FileByteStore(File dir) {
        this.dir = dir;
    }

    private File file(String filename) {
        return new File(dir, filename);
    }

    @Override
    public ByteSink put(String filename) throws IOException {
        return Files.asByteSink(file(filename));
    }

    @Override
    public ByteSource get(String filename) {
        return Files.asByteSource(file(filename));
    }
}
