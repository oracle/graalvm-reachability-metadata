/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.matching.ContainsPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.junit.jupiter.api.Test;

public class StringValuePatternJsonDeserializerTest {
    @Test
    void deserializesSimpleStringMatcherFromJson() {
        StringValuePattern pattern = Json.read("""
                {
                  "contains": "needle"
                }
                """, StringValuePattern.class);

        assertThat(pattern).isInstanceOf(ContainsPattern.class);
        assertThat(((ContainsPattern) pattern).getContains()).isEqualTo("needle");
        assertThat(pattern.match("haystack with needle").isExactMatch()).isTrue();
        assertThat(pattern.match("haystack").isExactMatch()).isFalse();
    }
}
