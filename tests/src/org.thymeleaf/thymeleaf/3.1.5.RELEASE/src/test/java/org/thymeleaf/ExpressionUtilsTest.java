/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.thymeleaf;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.thymeleaf.util.ExpressionUtils.isMemberForbidden;

/**
 * Not including test for {@link java.util.Set} here, as it doesn't have any methods of its own.
 *
 * @see org.thymeleaf.util.ExpressionUtils#ALLOWED_JAVA_SUPERS
 */
public class ExpressionUtilsTest {

    @Test
    void allowsCollectionMethods() {
        assertThat(isMemberForbidden(Collections.emptySet(), "isEmpty")).isFalse();
    }

    @Test
    void allowsIterableMethods() {
        assertThat(isMemberForbidden(Collections.emptyList(), "iterator")).isFalse();
    }

    @Test
    void allowsListMethods() {
        assertThat(isMemberForbidden(Collections.emptyList(), "get")).isFalse();
    }

    @Test
    void allowsMapMethods() {
        assertThat(isMemberForbidden(new HashMap<>(), "put")).isFalse();
    }

    @Test
    void allowsLocaleMethods() {
        assertThat(isMemberForbidden(Locale.getDefault(), "getDisplayName")).isFalse();
    }

    @Test
    void allowsDateMethods() {
        assertThat(isMemberForbidden(new Date(), "getTime")).isFalse();
    }
}
