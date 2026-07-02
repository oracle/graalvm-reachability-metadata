/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.AuthChallenge;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.AuthScheme;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.AuthenticationException;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.CredentialsProvider;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.MalformedChallengeException;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpHost;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpRequest;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicAuthCacheTest {
    @Test
    void storesSerializableAuthSchemeAsSerializedCopy() {
        BasicAuthCache authCache = new BasicAuthCache();
        HttpHost host = new HttpHost("https", "registry.example.test", -1);
        SerializableAuthScheme authScheme = new SerializableAuthScheme("custom-basic", "docker-registry");

        authCache.put(host, authScheme);
        AuthScheme cachedScheme = authCache.get(host);

        assertThat(cachedScheme).isInstanceOf(SerializableAuthScheme.class);
        assertThat(cachedScheme).isNotSameAs(authScheme);
        assertThat(cachedScheme.getName()).isEqualTo("custom-basic");
        assertThat(cachedScheme.getRealm()).isEqualTo("docker-registry");
    }

    private static final class SerializableAuthScheme implements AuthScheme, Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final String realm;

        private SerializableAuthScheme(final String name, final String realm) {
            this.name = name;
            this.realm = realm;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isConnectionBased() {
            return false;
        }

        @Override
        public void processChallenge(final AuthChallenge authChallenge, final HttpContext context)
                throws MalformedChallengeException {
        }

        @Override
        public boolean isChallengeComplete() {
            return true;
        }

        @Override
        public String getRealm() {
            return realm;
        }

        @Override
        public boolean isResponseReady(
                final HttpHost host,
                final CredentialsProvider credentialsProvider,
                final HttpContext context) throws AuthenticationException {
            return true;
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public String generateAuthResponse(final HttpHost host, final HttpRequest request, final HttpContext context)
                throws AuthenticationException {
            return "Basic dXNlcjpwYXNzd29yZA==";
        }
    }
}
