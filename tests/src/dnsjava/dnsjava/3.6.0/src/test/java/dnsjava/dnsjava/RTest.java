/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xbill.DNS.dnssec.R;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class RTest {
    @Test
    void loadsBundledDnssecMessagesAndFormatsPlaceholders() {
        R.setBundle(null);
        R.setUseNeutralMessages(false);
        try {
            assertThat(R.get("failed.nsec3.none")).isEqualTo("No NSEC3 records");
            assertThat(R.get("dnskey.key_offtree", "signer.example.", "other.example."))
                    .isEqualTo("Signer name signer.example. is off-tree for other.example.");
        } finally {
            R.setBundle(null);
        }
    }
}
