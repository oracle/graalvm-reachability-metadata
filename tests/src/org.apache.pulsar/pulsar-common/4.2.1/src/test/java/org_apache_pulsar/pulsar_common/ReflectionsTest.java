/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

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
    private static final ClassLoader PULSAR_CLASS_LOADER = Reflections.class.getClassLoader();
    private static final String COMPRESSION_CODEC_NONE = CompressionCodecNone.class.getName();
    private static final String LONG_SCHEMA_VERSION = LongSchemaVersion.class.getName();
    private static final String REFLECTIONS = Reflections.class.getName();
    private static final String TOPIC_POLICIES = TopicPolicies.class.getName();

    @Test
    void createsInstancesWithDefaultAndParameterizedConstructors() {
        final CompressionCodec codec = Reflections.createInstance(
                COMPRESSION_CODEC_NONE, CompressionCodec.class, PULSAR_CLASS_LOADER);
        final Object utility = Reflections.createInstance(REFLECTIONS, PULSAR_CLASS_LOADER);
        final Object schemaVersion = Reflections.createInstance(
                LONG_SCHEMA_VERSION, PULSAR_CLASS_LOADER, new Object[] { 42L }, new Class<?>[] { long.class });

        assertThat(codec).isInstanceOf(CompressionCodecNone.class);
        assertThat(utility).isInstanceOf(Reflections.class);
        assertThat(schemaVersion).isEqualTo(new LongSchemaVersion(42L));
    }

    @Test
    void checksClassPresenceAndImplementedInterfaces() {
        assertThat(Reflections.classExists(REFLECTIONS)).isTrue();
        assertThat(Reflections.classExists("org.apache.pulsar.common.util.DoesNotExist")).isFalse();

        assertThat(Reflections.classImplementsIface(COMPRESSION_CODEC_NONE, CompressionCodec.class)).isTrue();
    }

    @Test
    void resolvesPrimitiveObjectAndArrayClassNames() throws Exception {
        final ClassLoader arrayFallbackClassLoader = new ArrayFallbackClassLoader(PULSAR_CLASS_LOADER);

        assertThat(Reflections.loadClass("int", PULSAR_CLASS_LOADER)).isEqualTo(int.class);
        assertThat(Reflections.loadClass("I", PULSAR_CLASS_LOADER)).isEqualTo(int.class);
        assertThat(Reflections.loadClass("L" + REFLECTIONS + ";", PULSAR_CLASS_LOADER)).isEqualTo(Reflections.class);
        assertThat(Reflections.loadClass(REFLECTIONS, PULSAR_CLASS_LOADER)).isEqualTo(Reflections.class);
        assertThat(Reflections.loadClass("[[L" + REFLECTIONS + ";", arrayFallbackClassLoader))
                .isEqualTo(Reflections[][].class);
    }

    @Test
    void returnsDeclaredFieldsFromClassHierarchy() {
        final List<String> fieldNames = Reflections.getAllFields(NonPersistentTopicStatsImpl.class).stream()
                .map(field -> field.getName())
                .toList();

        assertThat(fieldNames).contains("msgDropRate", "nonPersistentSubscriptions", "msgRateIn", "bytesInCounter");
    }

    private static final class ArrayFallbackClassLoader extends ClassLoader {
        private ArrayFallbackClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("[")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
