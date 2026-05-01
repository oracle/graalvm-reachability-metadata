/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.MissingResourceException;

import javax.jdo.JDOFatalInternalException;
import javax.jdo.spi.I18NHelper;

import org.junit.jupiter.api.Test;

public class I18NHelperTest {

    @Test
    void missingBundleLoadedThroughSystemClassLoaderReportsResourceFailure() {
        I18NHelper helper = I18NHelper.getInstance("org_datanucleus.javax_jdo.UnavailableI18NHelperBundle", null);

        assertThatThrownBy(() -> helper.msg("missing.key"))
                .isInstanceOf(JDOFatalInternalException.class)
                .hasMessageContaining("missing.key")
                .hasCauseInstanceOf(MissingResourceException.class);
    }
}
