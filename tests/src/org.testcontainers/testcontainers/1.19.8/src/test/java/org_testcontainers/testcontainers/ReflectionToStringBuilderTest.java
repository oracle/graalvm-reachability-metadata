/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.testcontainers.shaded.org.apache.commons.lang3.builder.ToStringStyle;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionToStringBuilderTest {
    @Test
    void describesPrivateFieldsWithoutDisclosingExcludedSecrets() {
        Credentials credentials = new Credentials("reader", "private-token");
        String description = ReflectionToStringBuilder.toString(credentials, ToStringStyle.SHORT_PREFIX_STYLE);
        assertThat(description).contains("username=reader", "token=private-token");
        String redacted = new ReflectionToStringBuilder(credentials, ToStringStyle.SHORT_PREFIX_STYLE)
                .setExcludeFieldNames("token").toString();
        assertThat(redacted).contains("username=reader").doesNotContain("token", "private-token");
    }

    public static class Credentials {
        private final String username;
        private final String token;

        public Credentials(String username, String token) {
            this.username = username;
            this.token = token;
        }
    }
}
