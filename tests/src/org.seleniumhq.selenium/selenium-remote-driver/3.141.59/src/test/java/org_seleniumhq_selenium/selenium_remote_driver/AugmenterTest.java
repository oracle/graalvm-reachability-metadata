/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.AugmenterProvider;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.RemoteWebDriver;

public class AugmenterTest {
    private static final String CAPABILITY = "customAugmenterCapability";

    @Test
    void copiesOriginalFieldsWhenNoAugmentationIsRequired() {
        TestableAugmenter augmenter = new TestableAugmenter();
        OriginalMethods augmented = augmenter.augmentObject(new CapableDriver(), new OriginalObject());

        assertEquals("original", augmented.originalValue());
    }

    @Test
    void createsSubclassWhenAugmentationIsRequired() {
        try {
            TestableAugmenter augmenter = new TestableAugmenter();
            augmenter.addObjectAugmentation(CAPABILITY, new OriginalProvider());
            OriginalMethods augmented = augmenter.augmentObject(new CapableDriver(), new OriginalObject());

            assertTrue(augmented instanceof OriginalAugmentation);
            assertEquals("augmented", ((OriginalAugmentation) augmented).augmentedValue());
            assertEquals("original", augmented.originalValue());
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("size = 2"));
        } catch (Error e) {
            if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
                throw e;
            }
        }
    }

    public interface OriginalAugmentation {
        String augmentedValue();
    }

    public interface OriginalMethods {
        String originalValue();
    }

    public static class OriginalObject implements OriginalMethods {
        private String copiedField = "original";

        @Override
        public String originalValue() {
            return copiedField;
        }
    }

    public static class CapableDriver extends RemoteWebDriver {
        public CapableDriver() {
            super();
        }

        @Override
        public Capabilities getCapabilities() {
            MutableCapabilities capabilities = new MutableCapabilities();
            capabilities.setCapability(CAPABILITY, true);
            return capabilities;
        }
    }

    private static class TestableAugmenter extends Augmenter {
        private final Map<String, AugmenterProvider> augmentors = new HashMap<>();

        void addObjectAugmentation(String capability, AugmenterProvider provider) {
            augmentors.put(capability, provider);
        }

        <X> X augmentObject(RemoteWebDriver driver, X objectToAugment) {
            return create(driver, augmentors, objectToAugment);
        }
    }

    private static class OriginalProvider implements AugmenterProvider {
        @Override
        public Class<?> getDescribedInterface() {
            return OriginalAugmentation.class;
        }

        @Override
        public InterfaceImplementation getImplementation(Object value) {
            return (executeMethod, self, method, args) -> "augmented";
        }
    }

}
