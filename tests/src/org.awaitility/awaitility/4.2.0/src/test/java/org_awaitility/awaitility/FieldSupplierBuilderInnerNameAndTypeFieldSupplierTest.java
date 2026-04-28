/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_awaitility.awaitility;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.awaitility.core.FieldSupplierBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.fieldIn;

public class FieldSupplierBuilderInnerNameAndTypeFieldSupplierTest {

    @Test
    void readsPrivateFieldSelectedByAnnotation() throws Exception {
        AnnotatedFieldHolder holder = new AnnotatedFieldHolder("dynamic value");
        FieldSupplierBuilder builder = fieldIn(holder);
        builder.ofType(String.class).andAnnotatedWith(SelectedField.class);

        FieldSupplierBuilder.NameAndTypeFieldSupplier<String> supplier = builder.new NameAndTypeFieldSupplier<>();

        assertThat(supplier.call()).isEqualTo("dynamic value");
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface SelectedField {
    }

    private static final class AnnotatedFieldHolder {

        @SelectedField
        private final String value;

        private AnnotatedFieldHolder(String value) {
            this.value = value;
        }
    }
}
