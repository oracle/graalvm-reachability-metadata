/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFileWatcher;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.util.Source;
import org.apache.logging.log4j.core.util.WatcherFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WatcherFactoryTest {
    @TempDir
    Path directory;

    @Test
    void constructsAWatcherThatDetectsConfigurationFileChanges() throws Exception {
        Path file = Files.writeString(directory.resolve("logging.xml"), "initial");
        Files.setLastModifiedTime(file, FileTime.fromMillis(10000));
        try (LoggerContext context = new LoggerContext("watcher")) {
            BuiltConfiguration configuration = ConfigurationBuilderFactory.newConfigurationBuilder().build(false);
            context.start(configuration);
            ConfigurationFileWatcher watcher = WatcherFactory.instantiate("file", ConfigurationFileWatcher.class,
                    configuration, () -> configuration, List.of(), 10000);
            watcher.watching(new Source(file.toFile()));
            assertFalse(watcher.isModified());
            Files.writeString(file, "updated");
            Files.setLastModifiedTime(file, FileTime.fromMillis(20000));
            assertTrue(watcher.isModified());
            watcher.fileModified(file.toFile());
            assertFalse(watcher.isModified());
        }
    }
}
