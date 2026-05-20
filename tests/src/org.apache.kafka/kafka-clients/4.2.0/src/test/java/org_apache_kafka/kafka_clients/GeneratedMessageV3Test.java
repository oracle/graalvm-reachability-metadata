/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.Profile;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ProfileContainer;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ProfilesData;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ResourceProfiles;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ScopeProfiles;
import org.apache.kafka.shaded.io.opentelemetry.proto.profiles.v1experimental.ValueType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageV3Test {

    @Test
    void buildsSerializesAndParsesShadedTelemetryMessage() throws Exception {
        ProfilesData data = sampleProfilesData();
        byte[] bytes = data.toByteArray();

        ProfilesData parsed = ProfilesData.parseFrom(bytes);

        assertThat(parsed).isEqualTo(data);
        assertThat(parsed.getResourceProfiles(0).getScopeProfiles(0).getProfiles(0).getProfile().getTimeNanos())
                .isEqualTo(123L);
    }

    static ProfilesData sampleProfilesData() {
        Profile profile = Profile.newBuilder()
                .addStringTable("samples")
                .addSampleType(ValueType.newBuilder()
                        .setType(0)
                        .setUnit(0))
                .setTimeNanos(123L)
                .build();
        return ProfilesData.newBuilder()
                .addResourceProfiles(ResourceProfiles.newBuilder()
                        .addScopeProfiles(ScopeProfiles.newBuilder()
                                .setSchemaUrl("https://opentelemetry.io/schemas/1.0.0")
                                .addProfiles(ProfileContainer.newBuilder()
                                        .setProfile(profile))))
                .build();
    }
}
