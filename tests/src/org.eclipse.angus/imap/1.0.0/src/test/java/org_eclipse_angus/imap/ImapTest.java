/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.imap.protocol.FLAGS;
import com.sun.mail.imap.protocol.FetchResponse;
import com.sun.mail.imap.protocol.IMAPResponse;
import jakarta.mail.Flags;
import org.junit.jupiter.api.Test;

public class ImapTest {
    @Test
    public void parsesMessageFlagsFromFetchResponse() throws Exception {
        FetchResponse response = new FetchResponse(new IMAPResponse("* 7 FETCH (FLAGS (\\Seen \\Answered project))"));

        assertThat(response.getNumber()).isEqualTo(7);
        assertThat(response.getItemCount()).isEqualTo(1);
        FLAGS flags = response.getItem(FLAGS.class);
        assertThat(flags).isNotNull();
        assertThat(flags.msgno).isEqualTo(7);
        assertThat(flags.getSystemFlags()).containsExactlyInAnyOrder(Flags.Flag.SEEN, Flags.Flag.ANSWERED);
        assertThat(flags.getUserFlags()).containsExactly("project");
    }
}
