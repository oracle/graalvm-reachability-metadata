/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Locale;

import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.manager.util.SessionUtils;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionUtilsTest {

    @Test
    void discoversLocaleExposedByTapestrySessionAttribute() {
        StandardContext context = new StandardContext();
        StandardManager manager = new StandardManager();
        manager.setContext(context);
        StandardSession session = new StandardSession(manager);
        session.setValid(true);
        session.setAttribute("org.apache.tapestry.engine:app", new LocaleProvider(Locale.CANADA_FRENCH));

        assertThat(SessionUtils.guessLocaleFromSession((Session) session)).isEqualTo(Locale.CANADA_FRENCH);
    }

    public static class LocaleProvider {
        private final Locale locale;

        public LocaleProvider(Locale locale) {
            this.locale = locale;
        }

        public Locale getLocale() {
            return locale;
        }
    }
}
