package sdfs.store;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import java.io.IOException;
import java.nio.file.Path;

public interface ByteStore {

    ByteSink put(Path path) throws IOException;

    ByteSource get(Path path);

}
