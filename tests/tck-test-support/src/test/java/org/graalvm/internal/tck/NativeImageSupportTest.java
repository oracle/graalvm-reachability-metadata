/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeImageSupportTest {

    @Test
    void returnsFalseForNull() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(null));
    }

    @Test
    void returnsFalseForOutOfMemoryError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new OutOfMemoryError()));
    }

    @Test
    void returnsFalseForStackOverflowError() {
        assertFalse(NativeImageSupport.isUnsupportedFeatureError(new StackOverflowError()));
    }

    @Test
    void returnsFalseOutsideNativeImage() {
        assertFalse(NativeImageSupport.isInNativeImage());
    }

    @Test
    void returnsTrueWhenNativeImagePropertyIsSet() {
        String previousValue = System.getProperty("org.graalvm.nativeimage.imagecode");
        try {
            System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
            assertTrue(NativeImageSupport.isInNativeImage());
        } finally {
            if (previousValue == null) {
                System.clearProperty("org.graalvm.nativeimage.imagecode");
            } else {
                System.setProperty("org.graalvm.nativeimage.imagecode", previousValue);
            }
        }
    }
}
