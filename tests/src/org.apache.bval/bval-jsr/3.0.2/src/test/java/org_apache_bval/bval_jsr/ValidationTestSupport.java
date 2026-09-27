/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.bval.jsr.ApacheValidationProvider;

public final class ValidationTestSupport {

    private ValidationTestSupport() {
    }

    public static ValidatorFactory factory() {
        return Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory();
    }
}
