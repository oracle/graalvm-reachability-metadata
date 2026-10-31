/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleThreadFactoryTest {
    @TempDir
    Path directory;

    @Test
    void completesScansOnManagedWorkerThreads() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2);
                ScanResult scan = ScanSupport.graph(ScanSupport.archive(directory)).scanAsync(executor, 2)
                        .get(30, TimeUnit.SECONDS)) {
            assertEquals("reader", scan.getClassInfo(ScanFixtures.Subject.class.getName())
                    .getFieldInfo("name").loadClassAndGetField().get(new ScanFixtures.Subject()));
        }
    }
}
