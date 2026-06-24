/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.HttpConfig;
import org.eclipse.jgit.transport.HttpConfig.HttpRedirectMode;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpConfigTest {

    @Test
    void readsHttpRedirectModeFromConfig() throws Exception {
        Config config = new Config();
        config.setString(HttpConfig.HTTP, null, HttpConfig.FOLLOW_REDIRECTS_KEY,
                "true");
        config.setInt(HttpConfig.HTTP, null, HttpConfig.MAX_REDIRECTS_KEY, 3);

        HttpConfig httpConfig = new HttpConfig(config,
                new URIish("https://example.com/repository.git"));

        assertThat(httpConfig.getFollowRedirects()).isEqualTo(HttpRedirectMode.TRUE);
        assertThat(httpConfig.getMaxRedirects()).isEqualTo(3);
    }
}
