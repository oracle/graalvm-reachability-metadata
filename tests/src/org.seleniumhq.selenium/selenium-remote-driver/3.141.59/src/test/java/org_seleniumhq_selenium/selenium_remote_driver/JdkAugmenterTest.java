/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_remote_driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AugmenterProvider;
import org.openqa.selenium.remote.InterfaceImplementation;
import org.openqa.selenium.remote.JdkAugmenter;
import org.openqa.selenium.remote.RemoteWebDriver;

public class JdkAugmenterTest {
    private static final String CAPABILITY = "jdkAugmenterCapability";

    @Test
    void createsJdkProxyForAugmentedInterface() {
        JdkAugmenter augmenter = new JdkAugmenter();
        augmenter.addDriverAugmentation(CAPABILITY, new JdkProvider());
        WebDriver augmented = augmenter.augment(new JdkDriver());

        assertTrue(augmented instanceof JdkAugmentation);
        assertEquals("jdk", ((JdkAugmentation) augmented).jdkValue());
    }

    public interface JdkAugmentation {
        String jdkValue();
    }

    public static class JdkDriver extends RemoteWebDriver {
        public JdkDriver() {
            super();
        }

        @Override
        public Capabilities getCapabilities() {
            MutableCapabilities capabilities = new MutableCapabilities();
            capabilities.setCapability(CAPABILITY, true);
            return capabilities;
        }
    }

    private static class JdkProvider implements AugmenterProvider {
        @Override
        public Class<?> getDescribedInterface() {
            return JdkAugmentation.class;
        }

        @Override
        public InterfaceImplementation getImplementation(Object value) {
            return (executeMethod, self, method, args) -> "jdk";
        }
    }
}
