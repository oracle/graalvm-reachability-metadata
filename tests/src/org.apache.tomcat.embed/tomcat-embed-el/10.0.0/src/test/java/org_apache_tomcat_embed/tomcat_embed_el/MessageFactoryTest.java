/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import org.apache.el.util.MessageFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageFactoryTest {

    @Test
    void loadsMessagesFromBundle() {
        String message = MessageFactory.get("error.null");

        assertThat(message).isEqualTo("Expression cannot be null");
    }

    @Test
    void formatsMessageArguments() {
        String message = MessageFactory.get(
                "error.convert",
                "sample",
                String.class.getName(),
                Integer.class.getName());

        assertThat(message)
                .contains("sample")
                .contains(String.class.getName())
                .contains(Integer.class.getName());
    }

    @Test
    void returnsKeyWhenMessageIsMissing() {
        String key = "test.missing.message";

        assertThat(MessageFactory.get(key)).isEqualTo(key);
    }
}
