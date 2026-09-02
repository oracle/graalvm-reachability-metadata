/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_blackbird;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class BBDeserializerModifierTest {
    @Test
    void deserializesMethodBackedPrimitiveStringAndReferenceProperties() throws Exception {
        MutableBean bean = BBSerializerModifierTest.MAPPER.readValue(
                """
                {
                  "count": 23,
                  "sequence": 8000000002,
                  "active": true,
                  "name": "setter-path",
                  "detail": { "description": "nested-value" }
                }
                """,
                MutableBean.class);

        assertThat(bean.getCount()).isEqualTo(23);
        assertThat(bean.getSequence()).isEqualTo(8_000_000_002L);
        assertThat(bean.isActive()).isTrue();
        assertThat(bean.getName()).isEqualTo("setter-path");
        assertThat(bean.getDetail().getDescription()).isEqualTo("nested-value");
    }

    public static final class MutableBean {
        private int count;
        private long sequence;
        private boolean active;
        private String name;
        private Detail detail;

        public MutableBean() {
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public long getSequence() {
            return sequence;
        }

        public void setSequence(long sequence) {
            this.sequence = sequence;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Detail getDetail() {
            return detail;
        }

        public void setDetail(Detail detail) {
            this.detail = detail;
        }
    }

    public static final class Detail {
        private String description;

        public Detail() {
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
