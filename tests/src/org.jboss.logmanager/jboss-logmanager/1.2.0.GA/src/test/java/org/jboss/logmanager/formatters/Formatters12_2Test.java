/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager.formatters;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.security.PrivilegedAction;
import org.junit.jupiter.api.Test;

class Formatters12_2Test {

    @Test
    void privilegedExceptionClassResourceLookupUsesTheExceptionClassLoader() throws Exception {
        final FormatStep formatStep = Formatters.exceptionFormatStep(false, 0, 0, true);
        final Class<?> actionClass = Class.forName("org.jboss.logmanager.formatters.Formatters$12$2");
        final Constructor<?> constructor = actionClass.getDeclaredConstructor(
                formatStep.getClass(),
                Class.class,
                String.class
        );
        final String classResourceName = Formatters12_2Test.class.getName().replace('.', '/') + ".class";
        @SuppressWarnings("unchecked")
        final PrivilegedAction<URL> action = (PrivilegedAction<URL>) constructor.newInstance(
                formatStep,
                Formatters12_2Test.class,
                classResourceName
        );

        final URL resource = action.run();

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).contains("org/jboss/logmanager/formatters/Formatters12_2Test.class");
    }
}
