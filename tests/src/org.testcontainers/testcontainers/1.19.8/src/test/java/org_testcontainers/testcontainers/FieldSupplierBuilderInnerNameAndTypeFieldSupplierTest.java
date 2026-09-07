/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.shaded.org.awaitility.core.FieldSupplierBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldSupplierBuilderInnerNameAndTypeFieldSupplierTest {
    @Test
    void readsAFieldSelectedByAnnotation() throws Exception {
        FieldSupplierBuilder builder = Awaitility.fieldIn(new ValueHolder());
        builder.ofType(String.class).andAnnotatedWith(Selected.class);
        FieldSupplierBuilder.NameAndTypeFieldSupplier<String> supplier = builder.new NameAndTypeFieldSupplier<>();

        assertThat(supplier.call()).isEqualTo("selected");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Selected {}

    public static class ValueHolder {
        @Selected
        private final String value = "selected";
    }
}
