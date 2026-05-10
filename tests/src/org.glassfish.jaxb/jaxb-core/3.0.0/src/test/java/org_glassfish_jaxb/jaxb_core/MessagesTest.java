/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import org.glassfish.jaxb.core.marshaller.Messages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagesTest {
    @Test
    void formatsMarshallerMessagesFromResourceBundle() {
        String notMarshallable = Messages.format(Messages.NOT_MARSHALLABLE);
        String unsupportedEncoding = Messages.format(Messages.UNSUPPORTED_ENCODING, "UTF-16");
        String brokenDomImplementation = Messages.format(
                Messages.DOM_IMPL_DOESNT_SUPPORT_CREATELEMENTNS,
                "test-dom",
                "test-provider");

        assertThat(notMarshallable)
                .isNotBlank()
                .isNotEqualTo(Messages.NOT_MARSHALLABLE);
        assertThat(unsupportedEncoding)
                .contains("UTF-16")
                .doesNotContain("{0}");
        assertThat(brokenDomImplementation)
                .contains("test-dom", "test-provider")
                .doesNotContain("{0}", "{1}");
    }
}
