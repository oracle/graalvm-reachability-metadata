/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.jetty.util.IntrospectionUtil;
import org.eclipse.jetty.util.Utf8Appendable;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntrospectionUtilTest {
    @Test
    void findsAndUsesInheritedMethods() throws Exception {
        Utf8StringBuilder builder = new Utf8StringBuilder();
        Method append = IntrospectionUtil.findMethod(Utf8StringBuilder.class, "append",
                new Class<?>[]{String.class}, true, true);
        append.invoke(builder, "hello");
        assertEquals("hello", builder.toString());
        assertEquals(Utf8Appendable.class, append.getDeclaringClass());

        Method length = IntrospectionUtil.findMethod(Utf8StringBuilder.class, "length",
                new Class<?>[0], false, true);
        assertEquals(5, length.invoke(builder));
        assertTrue(IntrospectionUtil.containsSameMethodSignature(length, Utf8Appendable.class, true));
        assertTrue(IntrospectionUtil.containsSameMethodSignature(length, Utf8StringBuilder.class, true));
        assertFalse(IntrospectionUtil.containsSameMethodSignature(append, Utf8StringBuilder.class, true));
    }

    @Test
    void findsFieldsAndRecognizesTheirDeclaringTypes() throws Exception {
        Field replacement = IntrospectionUtil.findField(Utf8Appendable.class, "REPLACEMENT", char.class, false, true);
        assertEquals('\ufffd', replacement.getChar(null));
        Field inherited = IntrospectionUtil.findField(Utf8StringBuilder.class, "REPLACEMENT", char.class, true, true);
        assertEquals(replacement, inherited);
        assertEquals(Utf8Appendable.class, inherited.getDeclaringClass());
        assertTrue(IntrospectionUtil.containsSameFieldName(inherited, Utf8Appendable.class, true));
        assertFalse(IntrospectionUtil.containsSameFieldName(inherited, Utf8StringBuilder.class, true));
    }
}
