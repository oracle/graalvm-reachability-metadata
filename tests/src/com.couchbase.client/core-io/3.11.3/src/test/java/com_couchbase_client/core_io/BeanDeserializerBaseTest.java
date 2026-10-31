/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BeanDeserializerBaseTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Envelope envelope = mapper.readValue("""
                {"detail":{"code":"accepted"}}
                """, Envelope.class);
        assertEquals("accepted", envelope.detail.code);
        assertSame(envelope, envelope.detail.owner());
    }

    public static class Envelope {
        private Detail detail;

        public void setDetail(Detail detail) {
            this.detail = detail;
        }

        public class Detail {
            public String code;

            public Envelope owner() {
                return Envelope.this;
            }
        }
    }
}
