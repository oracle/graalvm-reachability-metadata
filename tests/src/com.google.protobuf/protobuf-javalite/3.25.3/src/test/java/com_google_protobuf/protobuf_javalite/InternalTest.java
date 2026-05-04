/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Empty;
import com.google.protobuf.Internal;
import org.junit.jupiter.api.Test;

public class InternalTest {
    @Test
    void getDefaultInstanceReflectivelyInvokesGeneratedMessageFactory() {
        Empty defaultInstance = Internal.getDefaultInstance(Empty.class);

        assertThat(defaultInstance).isSameAs(Empty.getDefaultInstance());
    }
}
