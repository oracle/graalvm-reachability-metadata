/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.hsqldb.resources.ResourceBundleHandler;
import org.junit.jupiter.api.Test;

public class ResourceBundleHandlerTest {
    @Test
    void getBundleUsesDefaultClassLoaderResourceLookup() {
        try {
            ResourceBundle bundle = ResourceBundleHandler.getBundle(Messages.class.getName(), Locale.ROOT, null);

            assertThat(bundle.getString("message")).isEqualTo("loaded");
        } catch (MissingResourceException exception) {
            assertThat(exception.getClassName()).isEqualTo(Messages.class.getName());
        }
    }

    @Test
    void getBundleUsesExplicitClassLoaderResourceLookup() {
        ClassLoader classLoader = ResourceBundleHandlerTest.class.getClassLoader();

        try {
            ResourceBundle bundle = ResourceBundleHandler.getBundle(Messages.class.getName(), Locale.ROOT, classLoader);

            assertThat(bundle.getString("message")).isEqualTo("loaded");
        } catch (MissingResourceException exception) {
            assertThat(exception.getClassName()).isEqualTo(Messages.class.getName());
        }
    }

    public static class Messages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                {"message", "loaded"}
            };
        }
    }
}
