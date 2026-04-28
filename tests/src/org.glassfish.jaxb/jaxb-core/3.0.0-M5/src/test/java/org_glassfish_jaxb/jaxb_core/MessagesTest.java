/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.jaxb.core.marshaller.Messages;
import org.junit.jupiter.api.Test;

public class MessagesTest {
    @Test
    public void loadsMarshallerMessageBundle() {
        String message = Messages.format(Messages.NOT_MARSHALLABLE);

        assertThat(message).isNotBlank();
    }

    @Test
    public void formatsMarshallerMessagesWithArguments() {
        String encodingMessage = Messages.format(Messages.UNSUPPORTED_ENCODING, "UTF-16");
        String domMessage = Messages.format(
                Messages.DOM_IMPL_DOESNT_SUPPORT_CREATELEMENTNS,
                "TestDomImplementation",
                "TestDomVendor");

        assertThat(encodingMessage).contains("UTF-16");
        assertThat(domMessage)
                .contains("TestDomImplementation")
                .contains("TestDomVendor");
    }
}
