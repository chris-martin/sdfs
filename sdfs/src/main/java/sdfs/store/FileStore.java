package sdfs.store;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FileStore implements ByteStore, StringStore, PathManipulator {

    private final Charset charset = StandardCharsets.UTF_8;

    private final Path rootPath;

    public FileStore(Path rootPath) {
        this.rootPath = rootPath;
    }

    private File file(Path path) {
        return rootPath.resolve(path).toFile();
    }

    public ByteSink put(Path path) throws IOException {
        File file = file(path);
        if (!file.isDirectory() && !file.getParentFile().mkdirs()) {
            throw new IOException("Cannot create parent directory");
        }
        return Files.asByteSink(file);
    }

    public ByteSource get(Path path) {
        return Files.asByteSource(file(path));
    }

    public String read(Path path) {

        path = rootPath.resolve(path);

        if (!path.toFile().exists()) {
            return null;
        }

        try {
            return new String(java.nio.file.Files.readAllBytes(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void write(Path path, String content) {

        path = rootPath.resolve(path);

        {
            File parent = path.getParent().toFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new RuntimeException("Failed to create directory at " + parent.getPath());
            }
        }

        try {
            java.nio.file.Files.write(path, content.getBytes(charset));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void move(Path source, Path target) throws IOException {
        java.nio.file.Files.move(source, target, REPLACE_EXISTING);
    }

    public boolean exists(Path path) {
        return path.toFile().exists();
    }

}
