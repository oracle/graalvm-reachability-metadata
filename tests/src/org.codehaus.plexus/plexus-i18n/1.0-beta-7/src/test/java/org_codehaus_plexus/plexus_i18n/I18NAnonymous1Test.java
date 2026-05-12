/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.i18n.I18N;
import org.junit.jupiter.api.Test;

public class I18NAnonymous1Test {
    @Test
    void initializesRoleFromI18NClassName() {
        assertThat(I18N.ROLE).isEqualTo(I18N.class.getName());
    }
}
