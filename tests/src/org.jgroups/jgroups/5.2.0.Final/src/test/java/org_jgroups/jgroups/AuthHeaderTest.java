/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.auth.RegexMembership;
import org.jgroups.protocols.AuthHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthHeaderTest {
    @Test
    void readsSerializedTokenUsingClassNameWhenNoMagicNumberIsRegistered() throws Exception {
        AuthHeader originalHeader = new AuthHeader(new RegexMembership());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(bytes)) {
            originalHeader.writeTo(output);
        }

        AuthHeader restoredHeader = new AuthHeader();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restoredHeader.readFrom(input);
        }

        assertThat(restoredHeader.getToken()).isInstanceOf(RegexMembership.class);
    }
}
