/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.hadoop.thirdparty.com.google.common.reflect.TypeParameter;
import org.apache.hadoop.thirdparty.com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Test;

public class TypesInnerJavaVersionAnonymous3Test {

    @Test
    void substitutedParameterizedTypeUsesJdkTypeNamesWhenFormatted() {
        TypeToken<List<String>> token = listOf(String.class);

        assertThat(token.getType().toString()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(token.toString()).isEqualTo("java.util.List<java.lang.String>");
    }

    private static <T> TypeToken<List<T>> listOf(Class<T> elementType) {
        return new TypeToken<List<T>>() { }
                .where(new TypeParameter<T>() { }, elementType);
    }
}
