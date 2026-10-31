/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.cfg.C3P0ConfigUtils;
import com.mchange.v2.c3p0.impl.C3P0Defaults;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class C3P0ConfigUtilsTest {
    @Test
    void extractsTypedAndStringifiedDefaultsForConfiguration() {
        Map<?, ?> typed = C3P0ConfigUtils.extractHardcodedC3P0Defaults(false);
        Map<?, ?> text = C3P0ConfigUtils.extractHardcodedC3P0Defaults(true);
        assertEquals(C3P0Defaults.maxPoolSize(), typed.get("maxPoolSize"));
        assertEquals(String.valueOf(C3P0Defaults.maxPoolSize()), text.get("maxPoolSize"));
        assertEquals(C3P0Defaults.testConnectionOnCheckout(), typed.get("testConnectionOnCheckout"));
        assertTrue(typed.containsKey("connectionTesterClassName"));
        assertEquals(typed.keySet(), text.keySet());
    }
}
