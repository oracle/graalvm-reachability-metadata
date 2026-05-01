/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CreatorInnerDelegatingTest {
    private static final String PAYLOAD_JSON = "{\"name\":\"delegated\",\"count\":7}";

    @Test
    void deserializesObjectThroughDelegatingConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ConstructorDelegatingValue value = mapper.readValue(PAYLOAD_JSON, ConstructorDelegatingValue.class);

        assertThat(value.getCreatorKind()).isEqualTo("constructor");
        assertThat(value.getPayload().getName()).isEqualTo("delegated");
        assertThat(value.getPayload().getCount()).isEqualTo(7);
    }

    @Test
    void deserializesObjectThroughDelegatingFactoryMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        FactoryDelegatingValue value = mapper.readValue(PAYLOAD_JSON, FactoryDelegatingValue.class);

        assertThat(value.getCreatorKind()).isEqualTo("factory");
        assertThat(value.getPayload().getName()).isEqualTo("delegated");
        assertThat(value.getPayload().getCount()).isEqualTo(7);
    }

    public static class ConstructorDelegatingValue {
        private final DelegatePayload payload;
        private final String creatorKind;

        @JsonCreator
        public ConstructorDelegatingValue(DelegatePayload payload) {
            this.payload = payload;
            this.creatorKind = "constructor";
        }

        public DelegatePayload getPayload() {
            return payload;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }

    public static class FactoryDelegatingValue {
        private final DelegatePayload payload;
        private final String creatorKind;

        private FactoryDelegatingValue(DelegatePayload payload, String creatorKind) {
            this.payload = payload;
            this.creatorKind = creatorKind;
        }

        @JsonCreator
        public static FactoryDelegatingValue fromPayload(DelegatePayload payload) {
            return new FactoryDelegatingValue(payload, "factory");
        }

        public DelegatePayload getPayload() {
            return payload;
        }

        public String getCreatorKind() {
            return creatorKind;
        }
    }

    public static class DelegatePayload {
        private String name;
        private int count;

        public DelegatePayload() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
