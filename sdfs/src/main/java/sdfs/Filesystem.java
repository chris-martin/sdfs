package sdfs;

import java.nio.file.Path;

public interface Filesystem {

    String read(Path path);

    void write(Path path, String content);

}
