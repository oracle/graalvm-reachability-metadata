/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.Messages;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerModelInnerMessagesTest {
    private static final String BUNDLE_NAME = "info_picocli.picocli.CommandLineInnerModelInnerMessagesTestMessages";

    @Test
    void loadsResourceBundleByBaseName() {
        CommandSpec spec = CommandSpec.create().name("localized");

        Messages messages = new Messages(spec, BUNDLE_NAME);

        assertThat(messages.resourceBundle()).isNotNull();
        assertThat(messages.getString("greeting", "fallback")).isEqualTo("Hello from messages bundle");
    }

    @Test
    void acceptsExistingResourceBundle() {
        CommandSpec spec = CommandSpec.create().name("localized");
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME);

        Messages messages = new Messages(spec, bundle);

        assertThat(Messages.resourceBundle(messages)).isSameAs(bundle);
        assertThat(messages.getString("greeting", "fallback")).isEqualTo("Hello from messages bundle");
    }
}
