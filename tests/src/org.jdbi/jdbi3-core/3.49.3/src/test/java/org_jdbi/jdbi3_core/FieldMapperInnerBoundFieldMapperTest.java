/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.FieldMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldMapperInnerBoundFieldMapperTest {
    @Test
    void constructsAnObjectAndWritesMappedFields() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:bound_field_mapper;DB_CLOSE_DELAY=-1");

        Message message = jdbi.withHandle(handle -> handle.createQuery("select 41 sequence, 'ready' body")
                .map(FieldMapper.of(Message.class))
                .one());

        assertThat(message.sequence()).isEqualTo(41);
        assertThat(message.body()).isEqualTo("ready");
    }

    public static class Message {
        private int sequence;
        private String body;

        public Message() {}

        public int sequence() {
            return sequence;
        }

        public String body() {
            return body;
        }
    }
}
