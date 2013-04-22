package sdfs;

import com.google.common.base.Joiner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilesystemImpl implements Filesystem {

    private final Path rootPath;

    public FilesystemImpl(Path rootPath) {
        this.rootPath = rootPath;
    }

    public String read(Path path) {

        path = rootPath.resolve(path);

        if (!path.toFile().exists()) {
            return null;
        }

        try {
            return Joiner.on("").join(Files.readAllLines(path, Charset.forName("UTF-8")));
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
            Files.write(path, content.getBytes(Charset.forName("UTF-8")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
