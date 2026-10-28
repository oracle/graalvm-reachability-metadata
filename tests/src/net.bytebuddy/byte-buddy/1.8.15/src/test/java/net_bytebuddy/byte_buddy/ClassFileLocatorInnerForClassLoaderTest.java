/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassFileLocatorInnerForClassLoaderTest {
    @Test
    void readsExistingLibraryClassResource() throws Exception {
        try (ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(getClass().getClassLoader())) {
            ClassFileLocator.Resolution resolution = locator.locate(JavaModule.class.getName());
            assertTrue(resolution.isResolved());
            ClassReader reader = new ClassReader(resolution.resolve());
            assertEquals("net/bytebuddy/utility/JavaModule", reader.getClassName());
            assertEquals("java/lang/Object", reader.getSuperName());
            assertFalse(locator.locate("missing.bytebuddy.NoSuchType").isResolved());
        }
    }
}
