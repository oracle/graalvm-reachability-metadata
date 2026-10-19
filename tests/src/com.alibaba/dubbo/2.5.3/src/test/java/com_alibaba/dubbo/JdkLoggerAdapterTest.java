/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.logger.Level;
import com.alibaba.dubbo.common.logger.jdk.JdkLoggerAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JdkLoggerAdapterTest {
    @Test
    void configuresAndCreatesJdkLoggers(@TempDir Path directory) throws IOException {
        File logFile = directory.resolve("dubbo.log").toFile();
        ExposedFileHandler handler = new ExposedFileHandler(logFile);
        java.util.logging.Logger global = java.util.logging.Logger.getLogger("global");
        global.addHandler(handler);
        try {
            JdkLoggerAdapter adapter = new JdkLoggerAdapter();
            adapter.setLevel(Level.WARN);

            assertThat(adapter.getLevel()).isEqualTo(Level.WARN);
            assertThat(adapter.getFile()).isEqualTo(logFile);
            assertThat(adapter.getLogger("dubbo-test")).isNotNull();
            assertThat(adapter.getLogger(JdkLoggerAdapterTest.class)).isNotNull();
        } finally {
            global.removeHandler(handler);
            handler.close();
        }
    }

    public static class ExposedFileHandler extends FileHandler {
        public final File[] files;

        public ExposedFileHandler(File file) throws IOException {
            super(file.getAbsolutePath());
            files = new File[] {file};
        }
    }
}
