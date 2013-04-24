package sdfs.store;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MockStore implements StringStore, ByteStore, PathManipulator {

    private interface File { }

    private static class StringFile implements File {

        final String x;

        private StringFile(String x) {
            this.x = x;
        }

    }

    private static class BytesFile implements File {

        final byte[] bytes;

        private BytesFile(byte[] bytes) {
            this.bytes = bytes;
        }

    }

    final Map<Path, File> files = new HashMap<>();

    public String read(Path path) {
        StringFile file = (StringFile) files.get(path);
        return file == null ? null : file.x;
    }

    public void write(Path path, String content) {
        files.put(path, new StringFile(content));
    }

    public ByteSink put(final Path path) throws IOException {
        return new ByteSink() {
            public OutputStream openStream() throws IOException {
                return new ByteArrayOutputStream() {
                    public void close() throws IOException {
                        files.put(path, new BytesFile(toByteArray()));
                    }
                };
            }
        };
    }

    public ByteSource get(final Path path) {
        return new ByteSource() {
            public InputStream openStream() throws IOException {
                return new ByteArrayInputStream(
                    ((BytesFile) files.get(path)).bytes
                );
            }
        };
    }

    public void move(Path source, Path target) throws IOException {
        files.put(target, files.remove(source));
    }

    public boolean exists(Path path) {
        return files.containsKey(path);
    }

}
