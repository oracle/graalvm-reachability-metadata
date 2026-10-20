/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.JavaType;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeFactoryTest {
    @Test
    void constructsTypesFromCanonicalClassNames() throws Exception {
        TypeFactory factory = TypeFactory.defaultInstance();
        JavaType listType = factory.constructFromCanonical("java.util.ArrayList<java.lang.String>");
        TypeFactory platformFirst = factory.withClassLoader(ClassLoader.getPlatformClassLoader());

        assertThat(listType.getRawClass()).isEqualTo(java.util.ArrayList.class);
        assertThat(listType.containedType(0).getRawClass()).isEqualTo(String.class);
        assertThat(factory.constructArrayType(String.class).getRawClass()).isEqualTo(String[].class);
        assertThat(platformFirst.findClass(TypeFactoryTest.class.getName())).isEqualTo(TypeFactoryTest.class);
    }
}
