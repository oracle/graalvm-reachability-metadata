/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.base.Enums;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumsTest {
    @Test
    void exposesEnumConstantDeclaration() {
        assertEquals("ACTIVE", Enums.getField(Status.ACTIVE).getName());
        assertEquals(Status.class, Enums.getField(Status.ACTIVE).getDeclaringClass());
        assertEquals(Status.ACTIVE, Enums.stringConverter(Status.class).convert("ACTIVE"));
    }

    public enum Status {
        ACTIVE, IDLE
    }
}
