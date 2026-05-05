/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AugmenterProvider;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.JdkAugmenter;
import org.openqa.selenium.remote.RemoteWebDriver;

public class JdkAugmenterInnerJdkHandlerTest {
    private static final String CAPABILITY = "jdkHandlerCapability";

    @Test
    void invokesOriginalMethodsThroughJdkHandler() {
        JdkAugmenter augmenter = new JdkAugmenter();
        augmenter.addDriverAugmentation(CAPABILITY, new HandlerProvider());
        WebDriver augmented = augmenter.augment(new HandlerDriver());

        assertEquals("augmented", ((HandlerAugmentation) augmented).augmentedValue());
        assertEquals("original", ((HandlerOriginal) augmented).originalValue());
    }

    public interface HandlerAugmentation {
        String augmentedValue();
    }

    public interface HandlerOriginal {
        String originalValue();
    }

    public static class HandlerDriver extends RemoteWebDriver implements HandlerOriginal {
        public HandlerDriver() {
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
            return "original";
        }
    }

    private static class HandlerProvider implements AugmenterProvider {
        @Override
        public Class<?> getDescribedInterface() {
            return HandlerAugmentation.class;
        }

        @Override
        public InterfaceImplementation getImplementation(Object value) {
            return (executeMethod, self, method, args) -> "augmented";
        }
    }
}
