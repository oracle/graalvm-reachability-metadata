/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.ProxyGeneratorAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ProxyGeneratorAdapterTest {
    @Test
    void adaptsAConstantDelegateToACallableClosure() {
        Closure<?> delegate = ProxyGeneratorAdapter.ensureClosure("parcel-ready");
        assertEquals("parcel-ready", delegate.call());
        assertEquals("parcel-ready", delegate.call("ignored", 7));
    }

    @Test
    void preservesAnExistingClosureDelegate() {
        Closure<String> delegate = new Closure<String>(this) {
            @Override
            public String call(Object... arguments) {
                return "parcel-" + arguments[0];
            }
        };
        Closure<?> adapted = ProxyGeneratorAdapter.ensureClosure(delegate);
        assertSame(delegate, adapted);
        assertEquals("parcel-12", adapted.call(12));
    }
}
