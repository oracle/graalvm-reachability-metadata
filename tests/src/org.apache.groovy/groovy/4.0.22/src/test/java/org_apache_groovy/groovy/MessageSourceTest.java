/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.tools.shell.util.MessageSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageSourceTest {
    @Test
    void loadsAndFormatsMessagesFromAResourceBundle() {
        MessageSource source = new MessageSource("org_apache_groovy.groovy.messages");
        assertEquals("Welcome {0}", source.getMessage("welcome"));
        assertEquals("Welcome Mira", source.format("welcome", new Object[]{"Mira"}));
        assertEquals("Ready", source.getProperty("status"));
    }
}
