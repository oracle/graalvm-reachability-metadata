/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.map.jsontype.impl.ClassNameIdResolver;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassNameIdResolverTest {
    @Test
    void resolvesConcreteClassNameToJavaType() {
        TypeFactory typeFactory = TypeFactory.defaultInstance();
        JavaType baseType = typeFactory.constructType(Object.class);
        ClassNameIdResolver resolver = new ClassNameIdResolver(baseType, typeFactory);

        JavaType resolvedType = resolver.typeFromId(TypedValue.class.getName());

        assertThat(resolvedType.getRawClass()).isEqualTo(TypedValue.class);
    }

    public static class TypedValue {
        public String name;
        public int count;
    }
}
