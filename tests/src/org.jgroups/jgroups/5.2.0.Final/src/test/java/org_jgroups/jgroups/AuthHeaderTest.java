/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.Message;
import org.jgroups.auth.AuthToken;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.AuthHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthHeaderTest {
    @Test
    void readsUnregisteredAuthTokenFromSerializedClassName() throws Exception {
        RoundTripAuthToken token = new RoundTripAuthToken()
                .identifier(42)
                .secret("native-image-token");
        AuthHeader original = new AuthHeader(token);

        assertThat(ClassConfigurator.getMagicNumber(RoundTripAuthToken.class)).isNegative();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        original.writeTo(new DataOutputStream(bytes));

        AuthHeader restored = new AuthHeader();
        restored.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertThat(restored.getToken()).isInstanceOf(RoundTripAuthToken.class);
        RoundTripAuthToken restoredToken = (RoundTripAuthToken) restored.getToken();
        assertThat(restoredToken.identifier()).isEqualTo(42);
        assertThat(restoredToken.secret()).isEqualTo("native-image-token");
        assertThat(restoredToken.authenticate(token, null)).isTrue();
    }

    public static class RoundTripAuthToken extends AuthToken {
        private int identifier;
        private String secret = "";

        public RoundTripAuthToken identifier(int newIdentifier) {
            identifier = newIdentifier;
            return this;
        }

        public int identifier() {
            return identifier;
        }

        public RoundTripAuthToken secret(String newSecret) {
            secret = newSecret;
            return this;
        }

        public String secret() {
            return secret;
        }

        @Override
        public String getName() {
            return getClass().getName();
        }

        @Override
        public int size() {
            return Integer.BYTES + Short.BYTES + secret.length();
        }

        @Override
        public boolean authenticate(AuthToken token, Message msg) {
            if (!(token instanceof RoundTripAuthToken)) {
                return false;
            }
            RoundTripAuthToken other = (RoundTripAuthToken) token;
            return identifier == other.identifier && secret.equals(other.secret);
        }

        @Override
        public void writeTo(DataOutput out) throws IOException {
            out.writeInt(identifier);
            out.writeUTF(secret);
        }

        @Override
        public void readFrom(DataInput in) throws IOException {
            identifier = in.readInt();
            secret = in.readUTF();
        }
    }
}
