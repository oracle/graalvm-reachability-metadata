/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.lang.reflect.Method;

import org.apache.ibatis.javassist.util.proxy.RuntimeSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SecurityActionsTest {
    @Test
    void resolvesOverloadedMethodsByTheirDescriptors() throws Throwable {
        Formatter formatter = new Formatter();
        Method text = RuntimeSupport.findMethod(Formatter.class, "format",
                RuntimeSupport.makeDescriptor(new Class<?>[]{String.class}, String.class));
        Method number = RuntimeSupport.findMethod(Formatter.class, "format",
                RuntimeSupport.makeDescriptor(new Class<?>[]{int.class}, String.class));

        assertEquals(Formatter.class, text.getDeclaringClass());
        assertEquals(Formatter.class, number.getDeclaringClass());
        assertEquals("text: Ada", RuntimeSupport.default_interceptor.invoke(
                formatter, text, text, new Object[]{"Ada"}));
        assertEquals("number: 7", RuntimeSupport.default_interceptor.invoke(
                formatter, number, number, new Object[]{7}));
    }

    @Test
    void resolvesAnInheritedMethodForTheProceedingHandler() throws Throwable {
        DetailedFormatter formatter = new DetailedFormatter();
        Method inherited = RuntimeSupport.findSuperClassMethod(DetailedFormatter.class, "format",
                RuntimeSupport.makeDescriptor(new Class<?>[]{String.class}, String.class));

        assertEquals(Formatter.class, inherited.getDeclaringClass());
        assertEquals("text: Grace", RuntimeSupport.default_interceptor.invoke(
                formatter, inherited, inherited, new Object[]{"Grace"}));
    }

    public static class Formatter {
        public String format(String value) {
            return "text: " + value;
        }

        public String format(int value) {
            return "number: " + value;
        }
    }

    public static class DetailedFormatter extends Formatter {
    }
}
