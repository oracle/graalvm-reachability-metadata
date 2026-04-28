/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jdo.spi.I18NHelper;

import org.junit.jupiter.api.Test;

public class I18NHelperTest {
    private static final String TEST_BUNDLE = "org_datanucleus.javax_jdo.I18NHelperTestMessages";

    @Test
    void loadsNamedResourceBundleWithProvidedClassLoader() {
        I18NHelper helper = I18NHelper.getInstance(TEST_BUNDLE, getClass().getClassLoader());

        assertThat(helper.getResourceBundle().getBaseBundleName()).isEqualTo(TEST_BUNDLE);
        assertThat(helper.msg("plain")).isEqualTo("Metadata resource bundle loaded");
        assertThat(helper.msg("withArgument", "native image"))
                .isEqualTo("Metadata resource bundle loaded for native image");
    }
}
