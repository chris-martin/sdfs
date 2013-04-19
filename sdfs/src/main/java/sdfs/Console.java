package sdfs;

import com.google.common.base.Joiner;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;

public class Console {

    private final Config config;

    public Console(Config config) {
        this.config = config;
    }

    public void start() {
        System.out.println(config);
    }

    public static void main(String[] args) {
        new Console(config(args)).start();
    }

    private static Config config(String[] args) {
        return argsConfig(args)
            .withFallback(fileConfig())
            .withFallback(ConfigFactory.load());
    }

    private static Config argsConfig(String[] args) {
        return ConfigFactory.parseString(Joiner.on("\n").join(args));
    }

    private static Config fileConfig() {
        return ConfigFactory.parseFile(new File("sdfs.conf"));
    }

}
