/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.pulsar.common.compression.CompressionCodec;
import org.apache.pulsar.common.compression.CompressionCodecNone;
import org.apache.pulsar.common.policies.data.TopicPolicies;
import org.apache.pulsar.common.policies.data.stats.NonPersistentTopicStatsImpl;
import org.apache.pulsar.common.schema.LongSchemaVersion;
import org.apache.pulsar.common.util.Reflections;
import org.junit.jupiter.api.Test;

public class ReflectionsTest {
    private static final ClassLoader CLASS_LOADER = Reflections.class.getClassLoader();

    @Test
    void createsInstancesUsingAllConstructorForms() {
        final CompressionCodec codec = Reflections.createInstance(
                CompressionCodecNone.class.getName(), CompressionCodec.class, CLASS_LOADER);
        final Object policies = Reflections.createInstance(TopicPolicies.class.getName(), CLASS_LOADER);
        final Object version = Reflections.createInstance(
                LongSchemaVersion.class.getName(), CLASS_LOADER, new Object[] {42L}, new Class<?>[] {long.class});

        assertThat(codec).isInstanceOf(CompressionCodecNone.class);
        assertThat(policies).isInstanceOf(TopicPolicies.class);
        assertThat(version).isEqualTo(new LongSchemaVersion(42L));
    }

    @Test
    void resolvesClassesAndInspectsTheirFields() throws Exception {
        final String codecName = CompressionCodecNone.class.getName();

        assertThat(Reflections.classExists(codecName)).isTrue();
        assertThat(Reflections.classImplementsIface(codecName, CompressionCodec.class)).isTrue();
        assertThat(Reflections.loadClass("int", CLASS_LOADER)).isEqualTo(int.class);
        assertThat(Reflections.loadClass("L" + Reflections.class.getName() + ";", CLASS_LOADER))
                .isEqualTo(Reflections.class);
        assertThat(Reflections.loadClass("[[L" + Reflections.class.getName() + ";", CLASS_LOADER))
                .isEqualTo(Reflections[][].class);

        final List<String> fields = Reflections.getAllFields(NonPersistentTopicStatsImpl.class).stream()
                .map(field -> field.getName())
                .toList();
        assertThat(fields).contains("msgDropRate", "msgRateIn", "bytesInCounter");
    }
}
