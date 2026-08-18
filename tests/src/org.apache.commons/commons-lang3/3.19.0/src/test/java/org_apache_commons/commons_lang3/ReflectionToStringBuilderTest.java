/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {

    @Test
    public void reflectionToStringReadsPrivateFieldsAcrossTheClassHierarchy() {
        RankedRecord record = new RankedRecord("id-1", "alpha", "session-1");

        String description = ReflectionToStringBuilder.toString(record, ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(description)
                .contains("identifier=id-1")
                .contains("label=alpha")
                .doesNotContain("sessionToken");
    }

    @Test
    public void reflectionToStringCanIncludeTransientFieldsWhenRequested() {
        SessionRecord record = new SessionRecord("alpha", "session-1");

        String defaultDescription = ReflectionToStringBuilder.toString(record, ToStringStyle.SHORT_PREFIX_STYLE);
        String transientDescription = ReflectionToStringBuilder.toString(record, ToStringStyle.SHORT_PREFIX_STYLE, true);

        assertThat(defaultDescription)
                .contains("label=alpha")
                .doesNotContain("sessionToken");
        assertThat(transientDescription)
                .contains("label=alpha")
                .contains("sessionToken=session-1");
    }

    private static class IdentifiedRecord {
        private final String identifier;

        private IdentifiedRecord(String identifier) {
            this.identifier = identifier;
        }
    }

    private static final class RankedRecord extends IdentifiedRecord {
        private final String label;
        private transient String sessionToken;

        private RankedRecord(String identifier, String label, String sessionToken) {
            super(identifier);
            this.label = label;
            this.sessionToken = sessionToken;
        }
    }

    private static final class SessionRecord {
        private final String label;
        private transient String sessionToken;

        private SessionRecord(String label, String sessionToken) {
            this.label = label;
            this.sessionToken = sessionToken;
        }
    }
}
