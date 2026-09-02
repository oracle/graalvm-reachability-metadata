/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_blackbird;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class CrossLoaderAccessTest {
    @Test
    void roundTripsPropertiesDeclaredByAParentClass() throws Exception {
        InheritedMessage message = new InheritedMessage();
        message.setText("inherited-accessor");

        String json = BBSerializerModifierTest.MAPPER.writeValueAsString(message);
        InheritedMessage restored = BBSerializerModifierTest.MAPPER.readValue(json, InheritedMessage.class);

        assertThat(BBSerializerModifierTest.MAPPER.readTree(json).get("text").asText())
                .isEqualTo("inherited-accessor");
        assertThat(restored.getText()).isEqualTo("inherited-accessor");
    }

    public static class MessageBase {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static final class InheritedMessage extends MessageBase {
        public InheritedMessage() {
        }
    }
}
