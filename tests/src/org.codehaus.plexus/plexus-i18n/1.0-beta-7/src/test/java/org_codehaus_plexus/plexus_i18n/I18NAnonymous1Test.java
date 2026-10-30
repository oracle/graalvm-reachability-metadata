/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_i18n;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.i18n.I18N;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class I18NAnonymous1Test {
    private static final String MESSAGES = "org_codehaus_plexus.plexus_i18n.messages";

    @Test
    void looksUpTranslationServiceByItsPublicRole() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            I18N i18n = (I18N) container.lookup(I18N.ROLE);
            try {
                assertThat(i18n.getString(MESSAGES, Locale.US, "greeting")).isEqualTo("Howdy");
                assertThat(i18n.format(MESSAGES, Locale.US, "welcome", "Camille"))
                        .isEqualTo("Howdy, Camille!");
                assertThat(i18n.getLocale("fr-FR,en;q=0.5")).isEqualTo(Locale.FRANCE);
            } finally {
                container.release(i18n);
            }
        } finally {
            container.dispose();
        }
    }
}
