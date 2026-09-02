/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_blackbird;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

public class CrossLoaderAccessTest {
    private static final MethodHandles.Lookup PACKAGE_LOOKUP = MethodHandles.lookup()
            .dropLookupMode(MethodHandles.Lookup.PRIVATE);

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new PackageLookupBlackbirdModule())
            .build();

    @Test
    void roundTripsInheritedPropertiesWithPackageLookup() throws Exception {
        InheritedMessage message = new InheritedMessage();
        message.setText("inherited-accessor");

        String json = MAPPER.writeValueAsString(message);
        InheritedMessage restored = MAPPER.readValue(json, InheritedMessage.class);

        assertThat(MAPPER.readTree(json).get("text").asText()).isEqualTo("inherited-accessor");
        assertThat(restored.getText()).isEqualTo("inherited-accessor");
    }

    public static final class PackageLookupBlackbirdModule extends BlackbirdModule {
        @Override
        protected Supplier<MethodHandles.Lookup> findLookupSupplier() {
            return new PackageLookupSupplier();
        }
    }

    private static final class PackageLookupSupplier implements Supplier<MethodHandles.Lookup> {
        @Override
        public MethodHandles.Lookup get() {
            return PACKAGE_LOOKUP;
        }
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
