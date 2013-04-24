package sdfs.store;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MockStore implements StringStore {

    final Map<Path, String> strings = new HashMap<>();

    public String read(Path path) {
        return strings.get(path);
    }

    public void write(Path path, String content) {
        strings.put(path, content);
    }

}
