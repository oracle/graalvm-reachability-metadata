/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.basic.logex.ExceptionWrapper;
import org.glassfish.pfl.basic.logex.Log;
import org.glassfish.pfl.basic.logex.LogLevel;
import org.glassfish.pfl.basic.logex.WrapperGenerator;
import org.junit.jupiter.api.Test;

public class WrapperGeneratorTest {
    @ExceptionWrapper(idPrefix = "E")
    public interface Errors {
        @Log(level = LogLevel.WARNING, id = 1)
        IllegalArgumentException invalid(String detail);
    }

    @Test
    public void createsExceptionWrapperThroughPublicApi() {
        Errors errors = WrapperGenerator.makeWrapper(Errors.class);

        assertThat(errors.invalid("detail")).isInstanceOf(IllegalArgumentException.class);
    }
}
