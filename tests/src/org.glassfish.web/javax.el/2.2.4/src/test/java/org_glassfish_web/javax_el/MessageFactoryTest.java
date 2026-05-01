/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import com.sun.el.util.MessageFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageFactoryTest {

    @Test
    void loadsMessageBundleAndFormatsMessages() {
        String plainMessage = MessageFactory.get("error.syntax.set");
        String formattedMessage = MessageFactory.get(
                "error.method.notfound", String.class, "missingMethod", "java.lang.Integer");

        assertThat(plainMessage).isEqualTo("Illegal Syntax for Set Operation");
        assertThat(formattedMessage)
                .contains(String.class.toString())
                .contains("missingMethod")
                .contains("java.lang.Integer");
    }
}
