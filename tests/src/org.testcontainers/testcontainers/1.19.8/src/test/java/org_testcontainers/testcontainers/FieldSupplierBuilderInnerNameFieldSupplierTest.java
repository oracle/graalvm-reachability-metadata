/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldSupplierBuilderInnerNameFieldSupplierTest {
    @Test
    void readsAFieldSelectedByTypeAndAnnotation() throws Exception {
        Callable<String> supplier = Awaitility
            .fieldIn(new ValueHolder())
            .ofType(String.class)
            .andAnnotatedWith(Selected.class);

        assertThat(supplier.call()).isEqualTo("selected");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Selected {}

    public static class ValueHolder {
        @Selected
        private final String value = "selected";
    }
}
