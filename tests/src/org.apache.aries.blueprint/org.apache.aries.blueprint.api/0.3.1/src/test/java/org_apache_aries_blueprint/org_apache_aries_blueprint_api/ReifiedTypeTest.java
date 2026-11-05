/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_api;

import org.junit.jupiter.api.Test;
import org.osgi.service.blueprint.container.ReifiedType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ReifiedTypeTest {
    @Test
    void describesRawTypesWithoutGenericArguments() {
        ReifiedType type = new ReifiedType(List.class);

        assertSame(List.class, type.getRawClass());
        assertEquals(0, type.size());
        assertSame(Object.class, type.getActualTypeArgument(0).getRawClass());
        assertSame(Object.class, type.getActualTypeArgument(1).getRawClass());
        assertEquals(0, type.getActualTypeArgument(0).size());
    }

    @Test
    void preservesPrimitiveAndArrayClasses() {
        ReifiedType primitive = new ReifiedType(int.class);
        ReifiedType array = new ReifiedType(String[].class);

        assertSame(int.class, primitive.getRawClass());
        assertSame(String[].class, array.getRawClass());
        assertEquals(0, primitive.size());
        assertEquals(0, array.size());
    }
}
