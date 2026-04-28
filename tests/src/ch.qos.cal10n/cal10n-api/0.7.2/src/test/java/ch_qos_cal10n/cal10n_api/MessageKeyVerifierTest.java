/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_cal10n.cal10n_api;

import static java.util.Locale.US;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ch.qos.cal10n.BaseName;
import ch.qos.cal10n.Locale;
import ch.qos.cal10n.LocaleData;
import ch.qos.cal10n.verifier.Cal10nError;
import ch.qos.cal10n.verifier.MessageKeyVerifier;
import org.junit.jupiter.api.Test;

public class MessageKeyVerifierTest {
    @Test
    public void verifiesEnumLoadedByClassName() {
        MessageKeyVerifier verifier = new MessageKeyVerifier(VerifierMessages.class.getName());

        List<Cal10nError> errors = verifier.verify(US);

        assertThat(verifier.getEnumType()).isEqualTo(VerifierMessages.class);
        assertThat(verifier.getEnumTypeAsStr()).isEqualTo(VerifierMessages.class.getName());
        assertThat(errors).isEmpty();
    }

    @BaseName("cal10n.messages")
    @LocaleData({ @Locale("en_US") })
    private enum VerifierMessages {
        GREETING,
        FAREWELL
    }
}
