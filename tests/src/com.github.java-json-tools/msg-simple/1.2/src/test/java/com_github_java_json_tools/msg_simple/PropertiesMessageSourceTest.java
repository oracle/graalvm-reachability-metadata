/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_java_json_tools.msg_simple;

import com.github.fge.msgsimple.source.MessageSource;
import com.github.fge.msgsimple.source.PropertiesMessageSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesMessageSourceTest {
    private static final String RESOURCE_PATH = "/com_github_java_json_tools/msg_simple/properties-message-source.properties";

    @Test
    void fromResourceLoadsMessagesFromClasspathPropertiesFile() throws Exception {
        MessageSource source = PropertiesMessageSource.fromResource(RESOURCE_PATH);

        assertThat(source.getKey("greeting")).isEqualTo("Hello from a test resource");
        assertThat(source.getKey("accented")).isEqualTo("caf\u00e9");
        assertThat(source.getKey("missing.key")).isNull();
    }
}
