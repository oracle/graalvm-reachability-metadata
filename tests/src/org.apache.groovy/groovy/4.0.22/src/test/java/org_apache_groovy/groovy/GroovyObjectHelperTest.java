/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyObjectSupport;
import org.apache.groovy.lang.GroovyObjectHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class GroovyObjectHelperTest {
    public static Lookup $getLookup() {
        return MethodHandles.lookup();
    }

    @Test
    void obtainsAndCachesAPrivateLookupForAGroovyObject() throws Throwable {
        LookupObject target = new LookupObject();
        Lookup lookup = GroovyObjectHelper.lookup(target).orElseThrow();
        assertEquals("private-value", lookup.findVirtual(LookupObject.class, "value", MethodType.methodType(String.class)).invoke(target));
        assertSame(lookup, GroovyObjectHelper.lookup(target).orElseThrow());
    }

    @Test
    void obtainsALookupFromANonStaticGroovyObject() throws Throwable {
        InnerLookupObject target = new InnerLookupObject();
        Lookup lookup = GroovyObjectHelper.lookup(target).orElseThrow();
        assertEquals("inner-value", lookup.findVirtual(InnerLookupObject.class, "value", MethodType.methodType(String.class)).invoke(target));
    }

    public static class LookupObject extends GroovyObjectSupport {
        public static Lookup $getLookup() {
            return MethodHandles.lookup();
        }

        private String value() {
            return "private-value";
        }
    }

    public class InnerLookupObject extends GroovyObjectSupport {
        public Lookup $getLookup() {
            return MethodHandles.lookup();
        }

        private String value() {
            return "inner-value";
        }
    }
}
