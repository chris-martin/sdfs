package sdfs.store;

import java.io.IOException;
import java.nio.file.Path;

public interface PathManipulator {

    void move(Path source, Path target) throws IOException;

    void delete(Path path) throws IOException;

    boolean exists(Path path);

}
