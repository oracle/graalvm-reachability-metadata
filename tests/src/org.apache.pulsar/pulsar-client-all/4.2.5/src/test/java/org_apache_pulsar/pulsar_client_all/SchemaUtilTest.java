/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {
    @Test
    void derivesAnAvroSchemaAndRoundTripsAPojo() {
        Schema<Account> schema = Schema.AVRO(Account.class);
        Account input = new Account("Ada", 7);

        Account restored = schema.decode(schema.encode(input));

        assertThat(restored.name).isEqualTo("Ada");
        assertThat(restored.level).isEqualTo(7);
        assertThat(schema.getSchemaInfo().getSchemaDefinition()).contains("Account", "name", "level");
    }

    public static class Account {
        public String name;
        public int level;

        public Account() {
        }

        Account(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }
}
