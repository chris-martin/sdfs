package sdfs.store;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MockStringStore implements StringStore {

    final Map<Path, String> data = new HashMap<>();

    public String read(Path path) {
        return data.get(path);
    }

    public void write(Path path, String content) {
        data.put(path, content);
    }

}
