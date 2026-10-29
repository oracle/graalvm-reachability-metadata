/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.jupiter.api.Test;

public class VersionUtilsTest {
    @Test
    void buildsClientIdentificationFromPackagedVersion() {
        String identity = VersionUtils.getFullClientVersion();
        assertTrue(identity.startsWith("Nacos-Java-Client:v"));
        assertFalse(VersionUtils.version.isBlank());
        assertTrue(identity.endsWith(VersionUtils.version));
    }
}
