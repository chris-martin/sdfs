package sdfs.store;

import java.nio.file.Path;

public interface StringStore {

    String read(Path path);

    void write(Path path, String content);

}
