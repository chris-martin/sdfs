package sdfs.store;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import java.io.IOException;

public interface Store {

    ByteSink put(String filename) throws IOException;

    ByteSource get(String filename);
}
