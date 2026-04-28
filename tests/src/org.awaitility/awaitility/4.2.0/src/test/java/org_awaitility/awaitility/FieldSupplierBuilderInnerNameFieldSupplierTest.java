/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_awaitility.awaitility;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.fieldIn;

public class FieldSupplierBuilderInnerNameFieldSupplierTest {

    @Test
    void readsPrivateFieldSelectedByTypeAndAnnotation() throws Exception {
        AnnotatedFieldHolder holder = new AnnotatedFieldHolder("ready");

        Callable<String> supplier = fieldIn(holder).ofType(String.class).andAnnotatedWith(SelectedField.class);

        assertThat(supplier.call()).isEqualTo("ready");
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
