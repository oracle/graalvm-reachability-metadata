/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.protobuf.Internal;
import org.apache.hadoop.thirdparty.protobuf.MessageLite;
import org.apache.hadoop.thirdparty.protobuf.StringValue;
import org.junit.jupiter.api.Test;

public class InternalTest {
    @Test
    void getDefaultInstanceReturnsRegisteredMessageDefault() {
        MessageLite defaultInstance = Internal.getDefaultInstance(StringValue.class);

        assertThat(defaultInstance).isSameAs(StringValue.getDefaultInstance());
    }
}
