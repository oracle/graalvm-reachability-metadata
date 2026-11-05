/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonIdentityInfo;
import io.trino.jdbc.$internal.jackson.annotation.ObjectIdGenerators;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.jackson.databind.annotation.JsonDeserialize;
import io.trino.jdbc.$internal.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FieldPropertyTest {
    @Test
    void bindsAndUpdatesPublicFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Account account = mapper.readValue("{\"name\":\"Ada\",\"balance\":42}", Account.class);
        assertEquals("Ada", account.name);
        assertEquals(42, account.balance);
        mapper.readerForUpdating(account).readValue("{\"balance\":73}");
        assertEquals(73, account.balance);
    }

    @Test
    void buildsValueFromPublicBuilderFields() throws Exception {
        Parcel parcel = new ObjectMapper().readValue("{\"label\":\"books\",\"weight\":12}", Parcel.class);
        assertEquals("books", parcel.label);
        assertEquals(12, parcel.weight);
    }

    @Test
    void bindsIdentityFieldAndResolvesSelfReference() throws Exception {
        FieldNode node = new ObjectMapper().readValue("{\"id\":\"root\",\"next\":\"root\"}", FieldNode.class);
        assertEquals("root", node.id);
        assertSame(node, node.next);
    }

    @JsonDeserialize(builder = ParcelBuilder.class)
    public static class Parcel {
        public final String label;
        public final int weight;

        public Parcel(String label, int weight) {
            this.label = label;
            this.weight = weight;
        }
    }

    @JsonPOJOBuilder
    public static class ParcelBuilder {
        public String label;
        public int weight;

        public Parcel build() {
            return new Parcel(label, weight);
        }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class FieldNode {
        public String id;
        public FieldNode next;
    }

    public static class Account {
        public String name;
        public int balance;
    }
}
