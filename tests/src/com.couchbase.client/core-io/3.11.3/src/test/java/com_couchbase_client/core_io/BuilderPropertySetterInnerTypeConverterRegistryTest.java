/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.env.BuilderPropertySetter.TypeConverterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BuilderPropertySetterInnerTypeConverterRegistryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        TypeConverterRegistry converter = new TypeConverterRegistry();
        assertEquals(42, converter.convert("42", Integer.class));
        assertArrayEquals(new Integer[]{2, 3, 5}, (Integer[]) converter.convert("2, 3, 5", Integer[].class));
    }
}
