/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

public class AugmenterInnerCompoundHandlerTest {
    private static final String CAPABILITY = "compoundHandlerCapability";

    @Test
    void dispatchesAugmentedAndOriginalMethodsThroughCompoundHandler() {
        try {
            Augmenter augmenter = new Augmenter();
            augmenter.addDriverAugmentation(CAPABILITY, new CompoundProvider());
            WebDriver augmented = augmenter.augment(new CompoundDriver());

            assertEquals("compound", ((CompoundAugmentation) augmented).compoundValue());
            assertEquals("driver", ((CompoundOriginal) augmented).driverValue());
        } catch (Error e) {
            if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
                throw e;
            }
        }
    }

    public interface CompoundAugmentation {
        String compoundValue();
    }

    public interface CompoundOriginal {
        String driverValue();
    }

    @Augmentable
    public static class CompoundDriver extends RemoteWebDriver implements CompoundOriginal {
        public CompoundDriver() {
            super();
        }

        @Override
        public Capabilities getCapabilities() {
            MutableCapabilities capabilities = new MutableCapabilities();
            capabilities.setCapability(CAPABILITY, true);
            return capabilities;
        }

        @Override
        public String driverValue() {
            return "driver";
        }
    }

    private static class CompoundProvider implements AugmenterProvider {
        @Override
        public Class<?> getDescribedInterface() {
            return CompoundAugmentation.class;
        }

        @Override
        public InterfaceImplementation getImplementation(Object value) {
            return (executeMethod, self, method, args) -> "compound";
        }
    }
}
