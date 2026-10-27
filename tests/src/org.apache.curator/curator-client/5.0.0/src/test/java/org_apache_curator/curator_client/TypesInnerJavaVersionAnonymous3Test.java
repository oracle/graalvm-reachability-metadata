/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.lang.reflect.Type;
import java.util.List;

import org.apache.curator.shaded.com.google.common.reflect.TypeResolver;
import org.apache.curator.shaded.com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesInnerJavaVersionAnonymous3Test {
    @Test
    void typeTokenRendersParameterizedTypeName() {
        TypeToken<List<String>> token = new TypeToken<List<String>>() { };
        Type resolvedType = new TypeResolver().resolveType(token.getType());

        assertThat(resolvedType.toString()).isEqualTo("java.util.List<java.lang.String>");
    }
}
