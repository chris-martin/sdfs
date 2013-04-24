package sdfs.store;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import java.io.IOException;

public interface ByteStore {

    ByteSink put(String filename) throws IOException;

    ByteSource get(String filename);
}
