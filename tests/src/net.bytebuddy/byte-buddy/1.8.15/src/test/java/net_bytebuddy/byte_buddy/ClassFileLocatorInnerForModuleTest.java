/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassFileLocatorInnerForModuleTest {
    @Test
    void locatesJdkClassInBootLayer() throws Exception {
        try (ClassFileLocator classPath = ClassFileLocator.ForClassLoader.ofClassPath();
             ClassFileLocator locator = ClassFileLocator.ForModule.ofBootLayer()) {
            byte[] classPathBytes = classPath.locate(String.class.getName()).resolve();
            ClassFileLocator.Resolution resolution = locator.locate(String.class.getName());
            assertTrue(resolution.isResolved());
            byte[] bytes = resolution.resolve();
            assertArrayEquals(classPathBytes, bytes);
            assertTrue(bytes.length > 8);
            assertArrayEquals(new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe},
                    new byte[]{bytes[0], bytes[1], bytes[2], bytes[3]});
            assertFalse(locator.locate("missing.bytebuddy.NoSuchType").isResolved());
        }
    }
}
