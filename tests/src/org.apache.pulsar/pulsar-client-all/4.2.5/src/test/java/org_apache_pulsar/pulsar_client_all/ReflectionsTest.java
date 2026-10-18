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
import org.apache.pulsar.common.schema.LongSchemaVersion;
import org.apache.pulsar.common.util.Reflections;
import org.junit.jupiter.api.Test;

public class ReflectionsTest {

    @Test
    void createsChecksAndInspectsPulsarTypesByName() throws Exception {
        final ClassLoader loader = Reflections.class.getClassLoader();
        final String codecName = CompressionCodecNone.class.getName();

        final CompressionCodec codec = Reflections.createInstance(codecName, CompressionCodec.class, loader);
        final LongSchemaVersion version = (LongSchemaVersion) Reflections.createInstance(
                LongSchemaVersion.class.getName(), loader, new Object[] {23L}, new Class<?>[] {long.class});
        final List<String> fields = Reflections.getAllFields(LongSchemaVersion.class).stream()
                .map(field -> field.getName())
                .toList();

        assertThat(codec).isInstanceOf(CompressionCodecNone.class);
        assertThat(version).isEqualTo(new LongSchemaVersion(23L));
        assertThat(Reflections.classExists(codecName)).isTrue();
        assertThat(Reflections.classImplementsIface(codecName, CompressionCodec.class)).isTrue();
        assertThat(Reflections.loadClass("[[L" + codecName + ";", loader))
                .isEqualTo(CompressionCodecNone[][].class);
        assertThat(fields).contains("version");
    }
}
