package sdfs;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.FileUtils;

import java.util.concurrent.TimeUnit;

public class Output {

    private Output() { }

    public static String transferRate(long size, Stopwatch stopwatch) {
        return transferSize(Math.round(size / (stopwatch.elapsed(TimeUnit.NANOSECONDS) / 1e9))) + "/s";
    }

    public static String transferSize(long size) {
        return FileUtils.byteCountToDisplaySize(size);
    }
}
