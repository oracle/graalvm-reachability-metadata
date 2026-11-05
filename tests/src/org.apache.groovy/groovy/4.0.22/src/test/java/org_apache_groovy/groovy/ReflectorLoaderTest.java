/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.metaclass.ReflectorLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ReflectorLoaderTest {
    @Test
    void findsRuntimeClassesWhenTheParentOnlyKnowsPlatformClasses() throws Exception {
        ReflectorLoader loader = new ReflectorLoader(ClassLoader.getPlatformClassLoader());
        assertSame(MetaClass.class, loader.loadClass(MetaClass.class.getName()));
        assertSame(String.class, loader.loadClass(String.class.getName()));
    }
}
