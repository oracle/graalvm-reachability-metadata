/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import org.apache.http.auth.AUTH;
import org.apache.http.auth.ChallengeState;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class RFC2617SchemeTest {

    @Test
    void serializesAndDeserializesBasicSchemeState() throws Exception {
        BasicScheme original = new BasicScheme(StandardCharsets.UTF_8);
        original.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"secure\""));

        BasicScheme restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getCredentialsCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(restored.getRealm()).isEqualTo("secure");
        assertThat(restored.getChallengeState()).isEqualTo(ChallengeState.TARGET);
    }

    private static byte[] serialize(BasicScheme scheme) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(buffer)) {
            outputStream.writeObject(scheme);
        }
        return buffer.toByteArray();
    }

    private static BasicScheme deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (BasicScheme) inputStream.readObject();
        }
    }
}
