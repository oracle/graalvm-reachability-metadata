/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.DelegatingMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallSiteArrayTest {
    @Test
    void dispatchesAStaticCallThroughTheRegisteredMetaclass() throws Throwable {
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass original = registry.getMetaClass(Labels.class);
        DelegatingMetaClass custom = new DelegatingMetaClass(original) {
            @Override
            public Object invokeStaticMethod(Object target, String name, Object[] arguments) {
                return "tracked:" + super.invokeStaticMethod(target, name, arguments);
            }
        };
        custom.initialize();
        try {
            registry.setMetaClass(Labels.class, custom);
            CallSiteArray sites = new CallSiteArray(getClass(), new String[]{"label"});
            assertEquals("tracked:order-17", sites.array[0].callStatic(Labels.class, 17));
        } finally {
            registry.setMetaClass(Labels.class, original);
        }
    }

    public static class Labels {
        public static String label(int number) {
            return "order-" + number;
        }
    }
}
