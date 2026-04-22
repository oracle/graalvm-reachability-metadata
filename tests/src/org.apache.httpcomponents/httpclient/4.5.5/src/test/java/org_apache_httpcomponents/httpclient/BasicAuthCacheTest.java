/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import org.apache.http.HttpHost;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.ChallengeState;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicAuthCacheTest {

    @Test
    void storesAndRestoresSerializableAuthSchemes() throws Exception {
        HttpHost host = new HttpHost("localhost", 8080, "http");
        BasicScheme scheme = new BasicScheme(StandardCharsets.UTF_8);
        scheme.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"cached\""));

        BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(host, scheme);

        AuthScheme restored = authCache.get(host);

        assertThat(restored).isInstanceOf(BasicScheme.class);
        assertThat(restored).isNotSameAs(scheme);
        BasicScheme restoredScheme = (BasicScheme) restored;
        assertThat(restoredScheme.getCredentialsCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(restoredScheme.getRealm()).isEqualTo("cached");
        assertThat(restoredScheme.getChallengeState()).isEqualTo(ChallengeState.TARGET);
    }
}
