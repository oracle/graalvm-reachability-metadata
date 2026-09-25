/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.authenticator.SingleSignOnEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleSignOnEntryTest {

    @Test
    void preservesSerializableAuthenticatedPrincipal() throws Exception {
        SingleSignOnEntry original = new SingleSignOnEntry(new UserPrincipal("alice"),
                HttpServletRequest.FORM_AUTH, "alice", "secret");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        SingleSignOnEntry restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (SingleSignOnEntry) input.readObject();
        }

        assertThat(restored.getPrincipal()).isEqualTo(new UserPrincipal("alice"));
        assertThat(restored.getUsername()).isEqualTo("alice");
        assertThat(restored.getPassword()).isEqualTo("secret");
        assertThat(restored.getCanReauthenticate()).isTrue();
    }

    private record UserPrincipal(String getName) implements Principal, Serializable {
    }
}
