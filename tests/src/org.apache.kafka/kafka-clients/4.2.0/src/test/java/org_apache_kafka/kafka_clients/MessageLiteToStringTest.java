/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.TextFormat;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ProfilesData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageLiteToStringTest {

    @Test
    void printsGeneratedTelemetryMessage() {
        ProfilesData data = GeneratedMessageV3Test.sampleProfilesData();

        String printed = TextFormat.printer().printToString(data);
        String simpleString = data.toString();

        assertThat(printed).contains("schema_url");
        assertThat(simpleString).contains("resource_profiles");
    }
}
