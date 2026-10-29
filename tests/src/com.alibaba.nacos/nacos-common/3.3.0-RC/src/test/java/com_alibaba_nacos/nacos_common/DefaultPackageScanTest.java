/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.model.core.IResultCode;
import com.alibaba.nacos.common.packagescan.DefaultPackageScan;
import com.alibaba.nacos.common.packagescan.resource.ClassPathResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultPackageScanTest {
    @TempDir
    Path directory;

    @Test
    void findsAnAnnotatedLibraryTypeInAResourceDirectory() throws Exception {
        String packagePath = "com/alibaba/nacos/common/model/core/";
        Path classFile = directory.resolve("IResultCode.class");
        ClassPathResource resource = new ClassPathResource(packagePath + "IResultCode.class");
        try (InputStream stream = resource.getInputStream()) {
            Files.copy(stream, classFile);
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(new ResourceDirectoryClassLoader(packagePath, directory.toUri().toURL(), previous));
        try {
            DefaultPackageScan scanner = new DefaultPackageScan();
            Set<Class<Object>> matches = scanner.getTypesAnnotatedWith("com.alibaba.nacos.common.model.core", Deprecated.class);
            assertEquals(Set.of(IResultCode.class), matches);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
