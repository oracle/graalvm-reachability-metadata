/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.impl.C3P0Defaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class C3P0DefaultsTest {
    @Test
    void recognizesConfigurationPropertiesAndRejectsUnknownNames() {
        assertTrue(C3P0Defaults.isKnownProperty("maxPoolSize"));
        assertTrue(C3P0Defaults.isKnownProperty("connectionTesterClassName"));
        assertTrue(C3P0Defaults.getKnownProperties(null).contains("checkoutTimeout"));
        assertFalse(C3P0Defaults.isKnownProperty("unrecognizedPoolSetting"));
        assertFalse(C3P0Defaults.isKnownProperty("getKnownProperties"));
    }
}
