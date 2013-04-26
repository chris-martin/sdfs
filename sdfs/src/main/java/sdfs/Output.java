package sdfs;

import com.google.common.base.Stopwatch;
import org.codeswarm.bytesize.ByteSizeFormat;
import org.codeswarm.bytesize.ByteSizeFormatBuilder;
import org.codeswarm.bytesize.ByteSizes;

import java.util.concurrent.TimeUnit;

public class Output {

    private Output() { }

    public static String transferRate(long size, Stopwatch stopwatch) {
        return transferSize(Math.round(size / (stopwatch.elapsed(TimeUnit.NANOSECONDS) / 1e9))) + "/s";
    }

    public static String transferSize(long size) {
        return byteSizeFormat.format(ByteSizes.byteSize(size), ByteSizeFormat.WordLength.ABBREVIATION);
    }
    private static final ByteSizeFormat byteSizeFormat = new ByteSizeFormatBuilder().build();
}
