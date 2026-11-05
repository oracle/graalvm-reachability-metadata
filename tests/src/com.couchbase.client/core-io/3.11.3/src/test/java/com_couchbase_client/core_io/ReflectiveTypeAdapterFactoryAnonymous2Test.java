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

public class ReflectiveTypeAdapterFactoryAnonymous2Test {
    @Test
    void mapsJson() {
        Gson gson = new Gson();
        Person value = gson.fromJson("""
                {"name":"Ada","age":37}
                """, Person.class);
        assertEquals("Ada", value.name);
        assertEquals(37, value.age);
        assertEquals(value.name, gson.fromJson(gson.toJson(value), Person.class).name);
        Point point = new Point(3, 7);
        assertEquals(point, gson.fromJson(gson.toJson(point), Point.class));
    }

    public static class Person {
        public String name;
        public int age;
    }

    public record Point(int x, int y) {
    }
}
