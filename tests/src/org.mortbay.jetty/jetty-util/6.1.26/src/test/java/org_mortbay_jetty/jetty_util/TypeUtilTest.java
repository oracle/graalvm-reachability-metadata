/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.util.TypeUtil;
import org.mortbay.util.UrlEncoded;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeUtilTest {
    @Test
    void resolvesKnownPrimitiveWrapperAndStringNames() {
        assertThat(TypeUtil.fromName("boolean")).isSameAs(Boolean.TYPE);
        assertThat(TypeUtil.fromName("byte")).isSameAs(Byte.TYPE);
        assertThat(TypeUtil.fromName("char")).isSameAs(Character.TYPE);
        assertThat(TypeUtil.fromName("double")).isSameAs(Double.TYPE);
        assertThat(TypeUtil.fromName("float")).isSameAs(Float.TYPE);
        assertThat(TypeUtil.fromName("int")).isSameAs(Integer.TYPE);
        assertThat(TypeUtil.fromName("long")).isSameAs(Long.TYPE);
        assertThat(TypeUtil.fromName("short")).isSameAs(Short.TYPE);
        assertThat(TypeUtil.fromName("void")).isSameAs(Void.TYPE);
        assertThat(TypeUtil.fromName("String")).isSameAs(String.class);

        assertThat(TypeUtil.toName(Boolean.TYPE)).isEqualTo("boolean");
        assertThat(TypeUtil.toName(Integer.class)).isEqualTo("java.lang.Integer");
        assertThat(TypeUtil.toName(String.class)).isEqualTo("java.lang.String");
    }

    @Test
    void convertsStringsWithBuiltInValueOfMethods() {
        assertThat(TypeUtil.valueOf(Boolean.TYPE, "true")).isEqualTo(Boolean.TRUE);
        assertThat(TypeUtil.valueOf(Byte.TYPE, "7")).isEqualTo(Byte.valueOf("7"));
        assertThat(TypeUtil.valueOf(Double.TYPE, "3.5")).isEqualTo(Double.valueOf("3.5"));
        assertThat(TypeUtil.valueOf(Float.TYPE, "2.25")).isEqualTo(Float.valueOf("2.25"));
        assertThat(TypeUtil.valueOf(Integer.TYPE, "42")).isEqualTo(Integer.valueOf("42"));
        assertThat(TypeUtil.valueOf(Long.TYPE, "123456789")).isEqualTo(Long.valueOf("123456789"));
        assertThat(TypeUtil.valueOf(Short.TYPE, "11")).isEqualTo(Short.valueOf("11"));

        assertThat(TypeUtil.valueOf(Boolean.class, "false")).isEqualTo(Boolean.FALSE);
        assertThat(TypeUtil.valueOf(Byte.class, "8")).isEqualTo(Byte.valueOf("8"));
        assertThat(TypeUtil.valueOf(Double.class, "4.5")).isEqualTo(Double.valueOf("4.5"));
        assertThat(TypeUtil.valueOf(Float.class, "6.75")).isEqualTo(Float.valueOf("6.75"));
        assertThat(TypeUtil.valueOf(Integer.class, "84")).isEqualTo(Integer.valueOf("84"));
        assertThat(TypeUtil.valueOf(Long.class, "987654321")).isEqualTo(Long.valueOf("987654321"));
        assertThat(TypeUtil.valueOf(Short.class, "12")).isEqualTo(Short.valueOf("12"));
    }

    @Test
    void convertsStringsWithSpecialCasesAndTypeNames() {
        assertThat(TypeUtil.valueOf(String.class, "plain text")).isEqualTo("plain text");
        assertThat(TypeUtil.valueOf(Character.TYPE, "jetty")).isEqualTo(Character.valueOf('j'));
        assertThat(TypeUtil.valueOf(Character.class, "util")).isEqualTo(Character.valueOf('u'));
        assertThat(TypeUtil.valueOf("int", "256")).isEqualTo(Integer.valueOf("256"));
    }

    @Test
    void convertsStringsWithPublicStringConstructors() {
        Object converted = TypeUtil.valueOf(UrlEncoded.class, "name=jetty&enabled=true");

        assertThat(converted).isInstanceOf(UrlEncoded.class);
        assertThat(((UrlEncoded) converted).getString("name")).isEqualTo("jetty");
        assertThat(((UrlEncoded) converted).getString("enabled")).isEqualTo("true");
    }

    @Test
    void returnsNullWhenNoStringConversionIsAvailable() {
        assertThat(TypeUtil.valueOf(Object.class, "ignored")).isNull();
    }
}
