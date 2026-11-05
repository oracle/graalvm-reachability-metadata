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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the pattern-flags subsystem to find case-insensitive alert records in a multiline feed.
 */
public class CaseInsensitiveAlertFeedTest {

    @Test
    @Timeout(60)
    void extractsAlertsFromMultilineFeedRegardlessOfCase() {
        String feed = "INFO service=payments\nALERT code=PAY-17 severity=CRITICAL\n"
                + "alert code=INV-04 severity=warning\nINFO service=search";
        AlertFeedReader reader = new AlertFeedReader();

        AlertSummary summary = reader.read(feed);

        assertEquals(2, summary.count());
        assertEquals("PAY-17:CRITICAL,INV-04:WARNING", summary.alerts());
    }

    /** Application reader that scans one alert record per line in an operator feed. */
    private static final class AlertFeedReader {

        private final Pattern alert = Pattern.compile(
                "^alert\\s+code=(?<code>[a-z0-9-]+)\\s+severity=(?<severity>critical|warning)$",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        AlertSummary read(String feed) {
            Matcher matcher = alert.matcher(feed);
            StringBuilder alerts = new StringBuilder();
            int count = 0;
            while (matcher.find()) {
                if (count > 0) {
                    alerts.append(',');
                }
                alerts.append(matcher.group("code"))
                        .append(':')
                        .append(matcher.group("severity").toUpperCase(Locale.ROOT));
                count++;
            }
            return new AlertSummary(count, alerts.toString());
        }
    }

    /** The alert facts an operator dashboard displays after reading one feed. */
    private record AlertSummary(int count, String alerts) {
    }
}
