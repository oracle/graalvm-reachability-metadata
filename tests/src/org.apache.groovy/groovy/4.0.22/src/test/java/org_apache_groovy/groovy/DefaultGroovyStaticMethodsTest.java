/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.DefaultGroovyStaticMethods;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultGroovyStaticMethodsTest {
    @Test
    void loadsMessagesUsingTheCallersClassLoader() {
        ResourceBundle bundle = DefaultGroovyStaticMethods.getBundle(null, "org_apache_groovy.groovy.messages", Locale.ROOT);
        assertEquals("Welcome {0}", bundle.getString("welcome"));
    }
}
