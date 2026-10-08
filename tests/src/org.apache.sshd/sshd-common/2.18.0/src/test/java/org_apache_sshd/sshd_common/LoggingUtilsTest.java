/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.util.logging.LoggingUtils;
import org.junit.jupiter.api.Test;

public class LoggingUtilsTest {
    @Test
    void buildsMessageMnemonicMapsFromPublicConstants() {
        assertThat(LoggingUtils.generateMnemonicMap(SshConstants.class, "SSH_MSG_"))
                .containsEntry(1, "SSH_MSG_DISCONNECT");
        assertThat(LoggingUtils.getAmbiguousMenmonics(SshConstants.class, "SSH_MSG_"))
                .containsEntry("SSH_MSG_KEXDH_INIT", 30)
                .containsEntry("SSH_MSG_KEX_DH_GEX_REQUEST_OLD", 30);
    }
}
