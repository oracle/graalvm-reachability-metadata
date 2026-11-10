/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Locale;

import org.apache.tomcat.util.res.StringManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringManagerTest {
    @Test
    void resolvesEnglishDiagnosticMessage() {
        StringManager messages = StringManager.getManager("org.apache.tomcat.util", Locale.ENGLISH);
        assertEquals("Garbage Collector [collector]", messages.getString("diagnostics.vmInfoGarbageCollectors", "collector"));
    }
}
