/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ReflectionHelperInnerRecordSupportedHelperTest {

    @Test
    void roundTripsRecordsBeansEnumsAndObjectArraysThroughGson() {
        final Gson gson = new Gson();
        final RecordPayload record = gson.fromJson(
                gson.toJson(new RecordPayload("record", 9, State.OPEN)), RecordPayload.class);
        final BeanPayload bean = gson.fromJson("{\"name\":\"bean\",\"state\":\"CLOSED\"}", BeanPayload.class);
        final RecordPayload[] records = gson.fromJson(
                "[{\"name\":\"first\",\"count\":1,\"state\":\"OPEN\"}]", RecordPayload[].class);

        assertThat(record).isEqualTo(new RecordPayload("record", 9, State.OPEN));
        assertThat(bean.name).isEqualTo("bean");
        assertThat(bean.state).isEqualTo(State.CLOSED);
        assertThat(records).containsExactly(new RecordPayload("first", 1, State.OPEN));
    }

    public enum State {
        OPEN,
        CLOSED
    }

    public record RecordPayload(String name, int count, State state) {
    }

    public static final class BeanPayload {
        private String name;
        private State state;

        public BeanPayload() {
        }
    }
}
