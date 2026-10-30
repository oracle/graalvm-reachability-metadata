/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VersionUtilsTest {
    @Test
    void loadsTheClientVersionFromThePackagedVersionFile() {
        assertNotNull(VersionUtils.version);
        assertFalse(VersionUtils.version.isBlank());
        assertNotEquals(VersionUtils.VERSION_PLACEHOLDER, VersionUtils.version);
        assertEquals("Nacos-Java-Client:v" + VersionUtils.version, VersionUtils.getFullClientVersion());
        assertEquals(0, VersionUtils.compareVersion(VersionUtils.version, VersionUtils.version));
    }
}
