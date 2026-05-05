/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sun.el.util.MessageFactory;

public class MessageFactoryTest {
    @Test
    void loadsDefaultMessagesBundleAndFormatsMessage() {
        String message = MessageFactory.get("error.convert", "value", String.class, Integer.class);

        assertThat(message).isEqualTo("Cannot convert value of type class java.lang.String to class java.lang.Integer");
    }
}
