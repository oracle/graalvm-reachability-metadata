/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.spi.ThrowableInformation;
import org.junit.jupiter.api.Test;

public class ThrowableInformationTest {

    @Test
    void rendersThrowableUsingCoreThrowablesWhenCoreIsAvailable() {
        Throwable throwable = new IllegalStateException("expected throwable message");
        ThrowableInformation information = new ThrowableInformation(throwable);

        String[] representation = information.getThrowableStrRep();

        assertThat(representation).isNotEmpty();
        assertThat(representation[0]).contains(IllegalStateException.class.getName(), "expected throwable message");
    }
}
