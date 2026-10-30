/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IOGroovyMethodsTest {
    @Test
    void writesAndIteratesAnObjectStream() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = IOGroovyMethods.newObjectOutputStream(bytes)) {
            IOGroovyMethods.leftShift(output, "first");
            IOGroovyMethods.leftShift(output, "second");
        }
        List<String> values = new ArrayList<>();
        try (ObjectInputStream input = IOGroovyMethods.newObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            IOGroovyMethods.eachObject(input, new Closure<Void>(this) {
                public Void doCall(String value) {
                    values.add(value);
                    return null;
                }
            });
        }
        assertEquals(List.of("first", "second"), values);
    }
}
