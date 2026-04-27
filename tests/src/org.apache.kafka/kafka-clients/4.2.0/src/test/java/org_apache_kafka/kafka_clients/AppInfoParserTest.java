/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.common.utils.AppInfoParser;
import org.junit.jupiter.api.Test;

public class AppInfoParserTest {

    @Test
    void getVersionAndCommitIdLoadKafkaVersionPropertiesFromClasspath() {
        assertThat(AppInfoParser.getVersion())
            .isNotBlank()
            .isNotEqualTo("unknown");
        assertThat(AppInfoParser.getCommitId())
            .isNotBlank()
            .isNotEqualTo("unknown");
    }
}
