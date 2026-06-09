/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.junit.jupiter.api.Test;

public class CredentialsTest {
    @Test
    void credentialsRoundTripTokensAndSecretKeys() throws Exception {
        Text alias = new Text("service-alias");
        Token<TokenIdentifier> token = new Token<>(
                "identifier".getBytes(StandardCharsets.UTF_8),
                "password".getBytes(StandardCharsets.UTF_8),
                new Text("kind"),
                new Text("service"));
        Credentials expected = new Credentials();
        expected.addToken(alias, token);
        expected.addSecretKey(new Text("secret"), "value".getBytes(StandardCharsets.UTF_8));

        DataOutputBuffer output = new DataOutputBuffer();
        expected.writeTokenStorageToStream(output);
        DataInputBuffer input = new DataInputBuffer();
        input.reset(output.getData(), output.getLength());
        Credentials actual = new Credentials();
        actual.readTokenStorageStream(input);

        assertThat(actual.numberOfTokens()).isEqualTo(1);
        assertThat(actual.getToken(alias).getKind()).isEqualTo(new Text("kind"));
        assertThat(actual.getToken(alias).getService()).isEqualTo(new Text("service"));
        assertThat(actual.getSecretKey(new Text("secret")))
                .isEqualTo("value".getBytes(StandardCharsets.UTF_8));
    }
}
