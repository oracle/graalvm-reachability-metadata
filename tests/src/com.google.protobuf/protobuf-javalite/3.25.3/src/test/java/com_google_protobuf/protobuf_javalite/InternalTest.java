/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Internal;
import com.google.protobuf.MessageLite;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

public class InternalTest {
    @Test
    void getDefaultInstanceReturnsGeneratedMessageDefaultInstance() {
        StringValue defaultInstance = Internal.getDefaultInstance(StringValue.class);

        assertThat(defaultInstance)
                .isSameAs(StringValue.getDefaultInstance())
                .isInstanceOf(MessageLite.class);
    }
}
