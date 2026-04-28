/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_transport_zerodep;

import java.nio.charset.StandardCharsets;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.AuthChallenge;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.AuthScheme;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.ChallengeType;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.auth.StandardAuthScheme;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.auth.BasicScheme;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpHost;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicAuthCacheTest {
    @Test
    void storesAndRestoresSerializableAuthSchemes() throws Exception {
        HttpHost host = new HttpHost("http", "cache.example", 8080);
        BasicScheme scheme = new BasicScheme(StandardCharsets.UTF_8);
        scheme.processChallenge(new AuthChallenge(
                ChallengeType.TARGET,
                StandardAuthScheme.BASIC,
                new BasicNameValuePair("realm", "cached-realm")), null);

        BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(host, scheme);

        AuthScheme restored = authCache.get(new HttpHost("http", "cache.example", 8080));

        assertThat(restored).isInstanceOf(BasicScheme.class);
        assertThat(restored).isNotSameAs(scheme);
        BasicScheme restoredScheme = (BasicScheme) restored;
        assertThat(restoredScheme.getName()).isEqualTo(StandardAuthScheme.BASIC);
        assertThat(restoredScheme.getRealm()).isEqualTo("cached-realm");
        assertThat(restoredScheme.isChallengeComplete()).isTrue();
    }
}
