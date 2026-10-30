/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessagesTest {
    @Test
    void unknownCommandLineOptionIsReported() {
        BadCommandLineException exception = assertThrows(BadCommandLineException.class,
                () -> new Options().parseArguments(new String[]{"-unknown-xjc-option"}));
        assertTrue(exception.getMessage().contains("-unknown-xjc-option"));
    }
}
