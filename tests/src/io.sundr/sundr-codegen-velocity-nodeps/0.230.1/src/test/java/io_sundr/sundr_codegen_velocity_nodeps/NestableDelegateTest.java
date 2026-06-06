/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.exception.NestableDelegate;
import io.sundr.deps.org.apache.commons.lang.exception.NestableException;
import org.junit.jupiter.api.Test;

public class NestableDelegateTest {

    @Test
    public void getMessageByIndexReadsNestedThrowableMessage() {
        IllegalArgumentException cause = new IllegalArgumentException("inner");
        NestableException exception = new NestableException("outer", cause);
        NestableDelegate delegate = new NestableDelegate(exception);

        String nestedMessage = delegate.getMessage(1);

        assertThat(nestedMessage).isEqualTo("inner");
    }
}
