/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.common.packagescan.resource.ClassPathResource;
import com.alibaba.nacos.common.utils.VersionUtils;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ClassPathResourceTest {
    @Test
    void opensLibraryResourcesByClassAndClassLoader() throws Exception {
        ClassPathResource[] resources = {
            new ClassPathResource("/nacos-version.txt", VersionUtils.class),
            new ClassPathResource("nacos-version.txt", VersionUtils.class.getClassLoader())
        };
        for (ClassPathResource resource : resources) {
            assertTrue(resource.exists());
            assertNotNull(resource.getUrl());
            try (InputStream input = resource.getInputStream()) {
                Properties properties = new Properties();
                properties.load(input);
                assertFalse(properties.getProperty("version").isBlank());
            }
        }
    }
}
