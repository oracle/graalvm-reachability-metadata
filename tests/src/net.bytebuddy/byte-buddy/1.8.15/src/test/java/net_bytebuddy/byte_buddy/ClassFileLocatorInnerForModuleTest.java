/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFileLocatorInnerForModuleTest {
    @Test
    void createsBootLayerLocatorAndUsesModulePackageMapping() throws Exception {
        try (ClassFileLocator classFileLocator = ClassFileLocator.ForModule.ofBootLayer()) {
            ClassFileLocator.Resolution resolution = classFileLocator.locate(
                    "java.lang.NoSuchByteBuddyType");

            assertThat(resolution.isResolved()).isFalse();
        }
    }
}
