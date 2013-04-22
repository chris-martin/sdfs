package sdfs;

import sdfs.filesystem.Filesystem;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MockFilesystem implements Filesystem {

    final Map<Path, String> data = new HashMap<>();

    public String read(Path path) {
        return data.get(path);
    }

    public void write(Path path, String content) {
        data.put(path, content);
    }

}
