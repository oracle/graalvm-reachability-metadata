/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufUnsafeUtilInnerAndroid32MemoryAccessorTest {

    @Test
    void generatedMessageMapParsingReadsDefaultEntryHolderStaticField() throws Exception {
        assertThat(Android32ProtobufMapParsing.parseMapEntryValue()).isEqualTo("eleven");
    }
}
