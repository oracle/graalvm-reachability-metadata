/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.reflection.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ReflectionUtilsTest {
    @Test
    void retainsUsableMethodsWhenABatchContainsAnEncapsulatedJdkMethod() throws Exception {
        Method length = String.class.getMethod("length");
        Method clone = Object.class.getDeclaredMethod("clone");
        AccessibleObject[] accessible = ReflectionUtils.makeAccessible(new Method[]{clone, length});
        assertInstanceOf(Method[].class, accessible);
        assertEquals(1, accessible.length);
        Method usable = (Method) accessible[0];
        assertEquals(length, usable);
        assertEquals(6, usable.invoke("parcel"));
    }
}
