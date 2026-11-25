/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Locale;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.manager.util.SessionUtils;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionUtilsTest {
    @Test
    void obtainsLocaleFromApplicationEngineSessionAttribute() {
        StandardManager manager = new StandardManager();
        manager.setContext(new StandardContext());
        StandardSession session = new StandardSession(manager);
        session.setValid(true);
        session.setAttribute("org.apache.tapestry.engine:catalog", new ApplicationEngine());
        assertThat(SessionUtils.guessLocaleFromSession(session.getSession())).isEqualTo(Locale.CANADA_FRENCH);
    }

    public static class ApplicationEngine {
        public Locale getLocale() {
            return Locale.CANADA_FRENCH;
        }
    }
}
