/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovySystem;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteClassLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class CallSiteClassLoaderTest {
    @Test
    void fallsBackToTheGroovyLoaderForAnApplicationOwnedRuntimeClass() throws Exception {
        CallSiteClassLoader loader = new CallSiteClassLoader(String.class);
        assertSame(GroovySystem.class, loader.loadClass(GroovySystem.class.getName()));
        assertSame(String.class, loader.loadClass(String.class.getName()));
    }

    @Test
    void resolvesRuntimeAndApplicationClassesWithoutIntroducingNewClasses() throws Exception {
        CallSiteClassLoader loader = new CallSiteClassLoader(CallSiteClassLoaderTest.class);
        assertSame(CallSite.class, loader.loadClass(CallSite.class.getName()));
        assertSame(getClass(), loader.loadClass(getClass().getName()));
    }
}
