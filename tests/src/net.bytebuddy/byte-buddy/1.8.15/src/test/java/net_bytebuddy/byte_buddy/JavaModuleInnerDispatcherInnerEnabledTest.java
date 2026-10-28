/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.InputStream;

import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.utility.JavaModule;
import net.bytebuddy.utility.JavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        try (ClassFileLocator locator = ClassFileLocator.ForModule.of(application)) {
            byte[] bytes = locator.locate(JavaType.class.getName()).resolve();
            assertTrue(bytes.length > 8);
            try (InputStream resource = application.getResourceAsStream("net/bytebuddy/utility/JavaType.class")) {
                assertNotNull(resource);
                assertArrayEquals(bytes, resource.readAllBytes());
            }
        }
    }
}
