/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Locale;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.manager.util.SessionUtils;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionUtilsTest {

    @Test
    void discoversLocaleFromTapestryEngineSessionAttribute() {
        StandardContext context = new StandardContext();
        StandardManager manager = new StandardManager();
        manager.setContext(context);
        StandardSession session = new StandardSession(manager);
        session.setValid(true);
        session.setAttribute("org.apache.tapestry.engine:main", new TapestryEngine(Locale.CANADA_FRENCH));

        Locale locale = SessionUtils.guessLocaleFromSession(session.getSession());

        assertThat(locale).isEqualTo(Locale.CANADA_FRENCH);
    }

    public static final class TapestryEngine {

        private final Locale locale;

        public TapestryEngine(Locale locale) {
            this.locale = locale;
        }

        public Locale getLocale() {
            return locale;
        }
    }
}
