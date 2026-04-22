/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jdeparser.jdeparser;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.jdeparser.FormatPreferences;
import org.junit.jupiter.api.Test;

public class FormatPreferencesTest {

    @Test
    void loadsPreferencesFromClassLoaderResource() throws Exception {
        FormatPreferences preferences = new FormatPreferences(FormatPreferencesTest.class.getClassLoader());

        assertThat(preferences.getLineLength()).isEqualTo(88);
        assertThat(preferences.getIndent(FormatPreferences.Indentation.LINE)).isEqualTo(2);
        assertThat(preferences.isIndentAbsolute(FormatPreferences.Indentation.LINE)).isTrue();
        assertThat(preferences.getWrapMode(FormatPreferences.Wrapping.EXCEPTION_LIST))
                .isEqualTo(FormatPreferences.WrappingMode.NEVER);
        assertThat(preferences.getSpaceType(FormatPreferences.Space.BEFORE_BRACE_CLASS))
                .isEqualTo(FormatPreferences.SpaceType.NONE);
        assertThat(preferences.hasOption(FormatPreferences.Opt.COMPACT_INIT_ONLY_CLASS)).isFalse();
    }
}
