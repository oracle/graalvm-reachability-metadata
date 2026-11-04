/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jruby_joni.joni;

import org.jcodings.specific.UTF8Encoding;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Syntax;
import org.joni.WarnCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises Joni's multiline anchor matching subsystem to read feature directives from a configuration document. */
public class MultilineDirectiveParsingTest {
    @Test
    @Timeout(60)
    public void readsOnlyCompleteFeatureDirectivesFromEachConfigurationLine() {
        byte[] pattern = "^feature=(?:on|off)$".getBytes(StandardCharsets.UTF_8);
        byte[] configuration = "mode=development\nfeature=on\nfeature=off\nfeature=on-extra\n"
                .getBytes(StandardCharsets.UTF_8);
        Regex directive = new Regex(
                pattern,
                0,
                pattern.length,
                Option.MULTILINE,
                UTF8Encoding.INSTANCE,
                Syntax.DEFAULT,
                WarnCallback.NONE);
        Matcher matcher = directive.matcher(configuration);

        assertEquals(configurationText(configuration).indexOf("feature=on"), matcher.search(
                0,
                configuration.length,
                Option.DEFAULT));
        assertEquals("feature=on", matchedText(configuration, matcher));

        int secondStart = configurationText(configuration).indexOf("feature=off");
        assertEquals(secondStart, matcher.search(
                matcher.getEnd(),
                configuration.length,
                Option.DEFAULT));
        assertEquals("feature=off", matchedText(configuration, matcher));
        assertEquals(Matcher.FAILED, matcher.search(
                matcher.getEnd(),
                configuration.length,
                Option.DEFAULT));
    }

    private static String configurationText(byte[] configuration) {
        return new String(configuration, StandardCharsets.UTF_8);
    }

    private static String matchedText(byte[] input, Matcher matcher) {
        return new String(
                input,
                matcher.getBegin(),
                matcher.getEnd() - matcher.getBegin(),
                StandardCharsets.UTF_8);
    }
}
