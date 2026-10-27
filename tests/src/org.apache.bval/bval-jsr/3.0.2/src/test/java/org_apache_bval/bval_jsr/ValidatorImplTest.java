/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import org.apache.bval.jsr.ApacheFactoryContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidatorImplTest {

    @Test
    void unwrapsConcreteExtensionUsingFactoryContextConstructor() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            ValidatorExtension extension = factory.getValidator().unwrap(ValidatorExtension.class);

            assertThat(extension.context).isNotNull();
        }
    }

    public static class ValidatorExtension {
        private final ApacheFactoryContext context;

        public ValidatorExtension(ApacheFactoryContext context) {
            this.context = context;
        }
    }
}
