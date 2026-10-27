/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.SshConstants;
import org.junit.jupiter.api.Test;

public class SshConstantsInnerLazyMessagesMapHolderTest {
    @Test
    void resolvesNonAmbiguousMessageNames() {
        assertThat(SshConstants.getCommandMessageName(SshConstants.SSH_MSG_DISCONNECT))
                .isEqualTo("SSH_MSG_DISCONNECT");
        assertThat(SshConstants.getAmbiguousOpcodes())
                .contains(30, 31, 60);
    }
}
