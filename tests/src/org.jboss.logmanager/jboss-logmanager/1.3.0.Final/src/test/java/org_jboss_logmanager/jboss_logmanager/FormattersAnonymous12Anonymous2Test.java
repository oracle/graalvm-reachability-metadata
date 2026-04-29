/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.security.PrivilegedAction;

import org.jboss.logmanager.formatters.FormatStep;
import org.jboss.logmanager.formatters.Formatters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormattersAnonymous12Anonymous2Test {

    @Test
    void resourceLookupUsesSuppliedClassLoader() throws Throwable {
        final String resourceName = FormattersAnonymous12Anonymous2Test.class.getName().replace('.', '/') + ".class";
        final ClassLoader classLoader = FormattersAnonymous12Anonymous2Test.class.getClassLoader();
        final URL expectedResource = classLoader.getResource(resourceName);

        final URL resource = newResourceLookupAction(classLoader, resourceName).run();

        assertThat(resource).isEqualTo(expectedResource);
    }

    @Test
    void resourceLookupUsesSystemClassLoaderWhenSuppliedClassLoaderIsNull() throws Throwable {
        final String resourceName = String.class.getName().replace('.', '/') + ".class";
        final URL expectedResource = ClassLoader.getSystemResource(resourceName);

        final URL resource = newResourceLookupAction(null, resourceName).run();

        assertThat(resource).isEqualTo(expectedResource);
    }

    private static PrivilegedAction<URL> newResourceLookupAction(final ClassLoader classLoader, final String resourceName)
            throws Throwable {
        final FormatStep exceptionFormatStep = Formatters.exceptionFormatStep(true, 0, 0, true);
        final Class<?> actionClass = exceptionFormatStep.getClass().getClassLoader()
                .loadClass(exceptionFormatStep.getClass().getName() + "$2");
        final MethodHandles.Lookup actionLookup = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup());
        final MethodHandle constructor = actionLookup.findConstructor(
                actionClass,
                MethodType.methodType(void.class, exceptionFormatStep.getClass(), ClassLoader.class, String.class)
        );

        @SuppressWarnings("unchecked")
        final PrivilegedAction<URL> action = (PrivilegedAction<URL>) constructor.invoke(
                exceptionFormatStep,
                classLoader,
                resourceName
        );
        return action;
    }
}
