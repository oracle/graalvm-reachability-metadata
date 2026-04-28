/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time.joda_time;

import static org.assertj.core.api.Assertions.assertThat;

import org.joda.time.DateTimeZone;
import org.joda.time.tz.DefaultNameProvider;
import org.joda.time.tz.NameProvider;
import org.joda.time.tz.Provider;
import org.joda.time.tz.UTCProvider;
import org.junit.jupiter.api.Test;

public class DateTimeZoneTest {

    @Test
    void loadsProviderFromConfiguredSystemProperty() {
        String propertyName = "org.joda.time.DateTimeZone.Provider";
        String previousPropertyValue = System.getProperty(propertyName);
        Provider previousProvider = DateTimeZone.getProvider();

        try {
            System.setProperty(propertyName, UTCProvider.class.getName());

            DateTimeZone.setProvider(null);

            assertThat(DateTimeZone.getProvider()).isInstanceOf(UTCProvider.class);
            assertThat(DateTimeZone.getProvider().getAvailableIDs()).containsExactly("UTC");
        } finally {
            restoreProperty(propertyName, previousPropertyValue);
            DateTimeZone.setProvider(previousProvider);
        }
    }

    @Test
    void loadsNameProviderFromConfiguredSystemProperty() {
        String propertyName = "org.joda.time.DateTimeZone.NameProvider";
        String previousPropertyValue = System.getProperty(propertyName);
        NameProvider previousNameProvider = DateTimeZone.getNameProvider();

        try {
            System.setProperty(propertyName, DefaultNameProvider.class.getName());

            DateTimeZone.setNameProvider(null);

            assertThat(DateTimeZone.getNameProvider()).isInstanceOf(DefaultNameProvider.class);
        } finally {
            restoreProperty(propertyName, previousPropertyValue);
            DateTimeZone.setNameProvider(previousNameProvider);
        }
    }

    private static void restoreProperty(String propertyName, String propertyValue) {
        if (propertyValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, propertyValue);
        }
    }
}
