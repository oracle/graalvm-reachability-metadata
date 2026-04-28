/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jdo.JDOUserException;
import javax.jdo.spi.JDOImplHelper;

import org.junit.jupiter.api.Test;

public class JDOImplHelperTest {
    @Test
    void constructsJdoExceptionFromStringConstructor() {
        String message = "identity key construction failed";

        Object constructed = JDOImplHelper.construct(JDOUserException.class.getName(), message);

        assertThat(constructed).isInstanceOf(JDOUserException.class);
        assertThat(((JDOUserException) constructed).getMessage()).isEqualTo(message);
    }
}
