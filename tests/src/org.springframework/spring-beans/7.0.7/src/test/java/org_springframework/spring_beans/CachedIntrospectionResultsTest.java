/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CachedIntrospectionResultsTest {
    @Test
    void exposesRecordStylePlainAccessorsAsReadOnlyProperties() {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(new Endpoint("primary", 8080));
        assertTrue(wrapper.isReadableProperty("name"));
        assertEquals("primary", wrapper.getPropertyValue("name"));
        assertEquals(8080, wrapper.getPropertyValue("port"));
    }

    public record Endpoint(String name, int port) {
    }
}
