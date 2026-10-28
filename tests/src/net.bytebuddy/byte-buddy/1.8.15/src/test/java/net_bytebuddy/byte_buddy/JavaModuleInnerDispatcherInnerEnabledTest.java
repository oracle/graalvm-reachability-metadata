/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaModuleInnerDispatcherInnerEnabledTest {
    @Test
    void inspectsModuleIdentityReadabilityAndResources() throws Exception {
        JavaModule base = JavaModule.ofType(String.class);
        JavaModule application = JavaModule.ofType(getClass());
        assertTrue(base.isNamed());
        assertEquals("java.base", base.getActualName());
        assertFalse(application.isNamed());
        assertEquals(getClass().getClassLoader(), application.getClassLoader());
        assertTrue(application.canRead(base));
        assertEquals(base, JavaModule.of(String.class.getModule()));
        try (InputStream resource = application.getResourceAsStream("byte-buddy-fixture.txt")) {
            assertNotNull(resource);
            assertEquals("byte-buddy resource fixture", new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
    }
}
