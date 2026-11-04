/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the named-capture span subsystem to index editable fields in a configuration line.
 */
public class NamedFieldSpanIndexTest {

    @Test
    @Timeout(60)
    void indexesFieldValuesWithTheirSourceSpans() {
        String configuration = "region=eu-west-1;owner=platform;retries=3";
        ConfigurationEditor editor = new ConfigurationEditor();

        List<FieldSpan> fields = editor.index(configuration);

        assertEquals(
                List.of(
                        new FieldSpan("region", "eu-west-1", 7, 16),
                        new FieldSpan("owner", "platform", 23, 31),
                        new FieldSpan("retries", "3", 40, 41)),
                fields);
    }

    /** Application editor that records where each configurable value appears in its source text. */
    private static final class ConfigurationEditor {

        private final Pattern field = Pattern.compile(
                "(?<name>[a-z]+)=(?<value>[a-z0-9-]+)(?:;|$)");

        List<FieldSpan> index(String configuration) {
            Matcher matcher = field.matcher(configuration);
            List<FieldSpan> fields = new ArrayList<>();
            while (matcher.find()) {
                fields.add(new FieldSpan(
                        matcher.group("name"),
                        matcher.group("value"),
                        matcher.start("value"),
                        matcher.end("value")));
            }
            return fields;
        }
    }

    /** A configuration value and its half-open span in the original source text. */
    private record FieldSpan(String name, String value, int start, int end) {
    }
}
