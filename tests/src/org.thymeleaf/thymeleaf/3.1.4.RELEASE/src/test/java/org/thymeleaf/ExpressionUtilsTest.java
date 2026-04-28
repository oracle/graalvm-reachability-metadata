/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.thymeleaf;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.thymeleaf.util.ExpressionUtils.isMemberAllowed;

/**
 * Not including test for {@link java.util.Set} here, as it doesn't have any methods of its own.
 *
 * @see org.thymeleaf.util.ExpressionUtils#ALLOWED_JAVA_SUPERS
 */
public class ExpressionUtilsTest {

    @Test
    void allowsCollectionMethods() {
        assertThat(isMemberAllowed(Collections.emptySet(), "isEmpty")).isTrue();
    }

    @Test
    void allowsIterableMethods() {
        assertThat(isMemberAllowed(Collections.emptyList(), "iterator")).isTrue();
    }

    @Test
    void allowsListMethods() {
        assertThat(isMemberAllowed(Collections.emptyList(), "get")).isTrue();
    }

    @Test
    void allowsMapMethods() {
        assertThat(isMemberAllowed(Collections.emptyMap(), "put")).isTrue();
    }

    @Test
    void allowsMapEntryMethods() {
        Map.Entry<String, String> entry = Collections.singletonMap("key", "value").entrySet().iterator().next();
        assertThat(isMemberAllowed(entry, "getKey")).isTrue();
    }

    @Test
    void allowsCalendarMethods() {
        assertThat(isMemberAllowed(Calendar.getInstance(), "clear")).isTrue();
    }
}
