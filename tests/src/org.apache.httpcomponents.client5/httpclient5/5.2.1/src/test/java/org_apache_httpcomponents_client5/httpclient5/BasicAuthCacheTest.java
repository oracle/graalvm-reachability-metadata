/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_client5.httpclient5;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicAuthCacheTest {

    @Test
    void storesSerializedAuthSchemeAndRestoresItOnLookup() throws Exception {
        final HttpHost host = new HttpHost("https", "example.test", 443);
        final BasicScheme scheme = new BasicScheme(StandardCharsets.UTF_8);
        scheme.processChallenge(new AuthChallenge(
                ChallengeType.TARGET,
                StandardAuthScheme.BASIC,
                new BasicNameValuePair("realm", "cached")), null);
        scheme.initPreemptive(new UsernamePasswordCredentials("\u00fcser", "p\u00e4ss".toCharArray()));

        final BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(host, "/secure", scheme);

        final AuthScheme restored = authCache.get(host, "/secure");

        assertThat(restored).isInstanceOf(BasicScheme.class);
        assertThat(restored).isNotSameAs(scheme);
        assertThat(restored.getRealm()).isEqualTo("cached");
        assertThat(restored.isChallengeComplete()).isTrue();
        final String expectedCredentials = Base64.getEncoder()
                .encodeToString("\u00fcser:p\u00e4ss".getBytes(StandardCharsets.UTF_8));
        assertThat(restored.generateAuthResponse(host, null, null))
                .isEqualTo(StandardAuthScheme.BASIC + " " + expectedCredentials);
    }
}
