/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.reflect.TypeResolver;
import com.couchbase.client.core.deps.com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypesInnerTypeVariableInvocationHandlerTest {
    @Test
    void resolvesBoundsUsingAnOuterTypeBinding() {
        TypeVariable<?> resolved = (TypeVariable<?>) new Bounds<Object>().resolve();
        assertEquals("U", resolved.getName());
        assertEquals(new TypeToken<List<String>>() { }.getType(), resolved.getBounds()[0]);
    }

    public static class Bounds<T> {
        public <U extends List<T>> Type resolve() {
            Type element = ((ParameterizedType) new TypeToken<List<T>>() { }.getType()).getActualTypeArguments()[0];
            Type variable = ((ParameterizedType) new TypeToken<List<U>>() { }.getType()).getActualTypeArguments()[0];
            return new TypeResolver().where(element, String.class).resolveType(variable);
        }
    }
}
