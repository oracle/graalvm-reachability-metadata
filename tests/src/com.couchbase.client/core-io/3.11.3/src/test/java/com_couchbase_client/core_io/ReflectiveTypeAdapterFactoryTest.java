/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectiveTypeAdapterFactoryTest {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        Employee employee = gson.fromJson("""
                {"name":"Ada","team":"storage"}
                """, Employee.class);
        assertEquals("Ada", employee.name);
        assertEquals("storage", employee.team);
        Employee restored = gson.fromJson(gson.toJson(employee), Employee.class);
        assertEquals(employee.name, restored.name);
        assertEquals(employee.team, restored.team);
    }

    public static class Person {
        public String name;
    }

    public static class Employee extends Person {
        public String team;
    }
}
