/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.Internal;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalTest {
    @Test
    void getDefaultInstanceInvokesGeneratedDefaultInstanceAccessor() {
        StringValue defaultInstance = Internal.getDefaultInstance(StringValue.class);

        assertThat(defaultInstance).isSameAs(StringValue.getDefaultInstance());
        assertThat(defaultInstance.getValue()).isEmpty();
    }
}
