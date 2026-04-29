/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.lang.reflect.Constructor;
import java.security.PrivilegedAction;

import jakarta.el.ImportHandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportHandlerInnerPrivilegedResourceExistsTest {

    @Test
    void privilegedActionFindsClassResource() throws Exception {
        String resourceName = ImportHandlerTest.StaticFieldTarget.class.getName().replace('.', '/') + ".class";
        PrivilegedAction<Boolean> resourceExists = createPrivilegedResourceExists(resourceName);

        assertThat(resourceExists.run()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static PrivilegedAction<Boolean> createPrivilegedResourceExists(String resourceName) throws Exception {
        Class<?> actionClass = Class.forName(ImportHandler.class.getName() + "$PrivilegedResourceExists");
        Constructor<?> constructor = actionClass.getDeclaredConstructor(ClassLoader.class, String.class);
        constructor.setAccessible(true);
        return (PrivilegedAction<Boolean>) constructor.newInstance(
                Thread.currentThread().getContextClassLoader(), resourceName);
    }
}
