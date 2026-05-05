/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.Augmentable;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.AugmenterProvider;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.RemoteWebDriver;

public class AugmenterTest {
    private static final String CAPABILITY = "customAugmenterCapability";

    @Test
    void augmentsDriverAndCopiesOriginalFields() {
        try {
            Augmenter augmenter = new Augmenter();
            augmenter.addDriverAugmentation(CAPABILITY, new CustomProvider());
            WebDriver augmented = augmenter.augment(new AugmentableDriver());

            assertTrue(augmented instanceof CustomAugmentation);
            assertEquals("handled", ((CustomAugmentation) augmented).augmentedValue());
            assertEquals("original", ((OriginalMethods) augmented).originalValue());
        } catch (Error e) {
            if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
                throw e;
            }
        }
    }

    public interface CustomAugmentation {
        String augmentedValue();
    }

    public interface OriginalMethods {
        String originalValue();
    }

    @Augmentable
    public static class AugmentableDriver extends RemoteWebDriver implements OriginalMethods {
        private String copiedField = "original";

        public AugmentableDriver() {
            super();
        }

        @Override
        public Capabilities getCapabilities() {
            MutableCapabilities capabilities = new MutableCapabilities();
            capabilities.setCapability(CAPABILITY, true);
            return capabilities;
        }

        @Override
        public String originalValue() {
            return copiedField;
        }
    }

    private static class CustomProvider implements AugmenterProvider {
        @Override
        public Class<?> getDescribedInterface() {
            return CustomAugmentation.class;
        }

        @Override
        public InterfaceImplementation getImplementation(Object value) {
            return (executeMethod, self, method, args) -> "handled";
        }
    }
}
