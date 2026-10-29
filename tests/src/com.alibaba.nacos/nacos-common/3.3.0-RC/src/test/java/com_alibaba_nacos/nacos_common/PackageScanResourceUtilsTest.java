/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.packagescan.util.ResourceUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PackageScanResourceUtilsTest {
    @TempDir
    Path directory;

    @Test
    void resolvesAFileBackedClasspathResource() throws Exception {
        Path settings = Files.writeString(directory.resolve("settings.properties"), "mode=cluster\n");
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader resources = new ResourceDirectoryClassLoader(
                "nacos-settings.properties", settings.toUri().toURL(), previous);
        thread.setContextClassLoader(resources);
        try {
            assertEquals(settings.toRealPath(), ResourceUtils.getFile("classpath:nacos-settings.properties").toPath().toRealPath());
            assertEquals(settings.toFile(), ResourceUtils.getFile(settings.toUri().toString()));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
