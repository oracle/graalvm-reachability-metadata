/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.shade.org.apache.commons.io.input.ClassLoaderObjectInputStream;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class ClassLoaderObjectInputStreamTest {

    @Test
    void resolvesSerializedClassesWithTheConfiguredLoader() throws Exception {
        final ArrayList<String> source = new ArrayList<>(List.of("created", "stored"));
        final byte[] serialized = SerializationUtils.serialize(source);

        try (ClassLoaderObjectInputStream input = new ClassLoaderObjectInputStream(
                getClass().getClassLoader(), new ByteArrayInputStream(serialized))) {
            assertThat(input.readObject()).isEqualTo(source);
        }
    }
}
