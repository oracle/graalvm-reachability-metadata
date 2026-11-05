/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.StreamGroovyMethods;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class StreamGroovyMethodsTest {
    @Test
    void createsTypedArraysFromStreams() {
        try (Stream<String> stream = Stream.of("north", "south")) {
            assertArrayEquals(new String[]{"north", "south"}, StreamGroovyMethods.toArray(stream, String.class));
        }
    }
}
