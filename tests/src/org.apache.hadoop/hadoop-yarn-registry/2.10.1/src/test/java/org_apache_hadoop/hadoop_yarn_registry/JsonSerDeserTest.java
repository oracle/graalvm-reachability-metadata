/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_yarn_registry;

import org.apache.hadoop.registry.client.binding.JsonSerDeser;
import org.apache.hadoop.registry.client.types.ServiceRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonSerDeserTest {
    @Test
    void fromResourceLoadsServiceRecordFromClasspathResource() throws Exception {
        JsonSerDeser<ServiceRecord> serializer = new JsonSerDeser<>(ServiceRecord.class);

        ServiceRecord record = serializer.fromResource("/org_apache_hadoop/hadoop_yarn_registry/service-record.json");

        assertThat(record.type).isEqualTo(ServiceRecord.RECORD_TYPE);
        assertThat(record.description).isEqualTo("registry entry loaded from a test resource");
        assertThat(record.get("application.id")).isEqualTo("application_0001_0001");
        assertThat(record.internal).isEmpty();
        assertThat(record.external).isEmpty();
    }
}
