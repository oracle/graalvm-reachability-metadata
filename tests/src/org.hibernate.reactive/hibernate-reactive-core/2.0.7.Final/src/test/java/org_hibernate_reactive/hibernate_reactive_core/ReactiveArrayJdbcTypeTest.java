/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import org.hibernate.reactive.type.descriptor.jdbc.ReactiveArrayJdbcType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReactiveArrayJdbcTypeTest {

    @Test
    void resolvesRecommendedArrayJavaTypeMapping() {
        TypeConfiguration typeConfiguration = new TypeConfiguration();

        JavaType<?> javaType = ReactiveArrayJdbcType.INSTANCE
                .getJdbcRecommendedJavaTypeMapping(null, null, typeConfiguration);

        Class<?> javaTypeClass = javaType.getJavaTypeClass();
        assertThat(javaTypeClass.isArray()).isTrue();
        assertThat(javaTypeClass.getComponentType()).isNotNull();
    }
}
