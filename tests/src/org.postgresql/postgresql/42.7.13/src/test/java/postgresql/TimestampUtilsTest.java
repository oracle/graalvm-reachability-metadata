/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.sql.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.TimestampUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.jdbc.TimestampUtils}.
 */
public class TimestampUtilsTest {

    @Test
    void binaryDateConversionUsesDefaultTimezoneFastPathWhenAvailable() throws Exception {
        String previousJavaVersion = System.getProperty("java.version");
        System.setProperty("java.version", "1.8.0");
        try {
            TimestampUtils timestampUtils = new TimestampUtils(false, TimeZone::getDefault);

            Date date = timestampUtils.toDateBin(null, new byte[] {0, 0, 0, 0});

            assertThat(date).isNotNull();
        } finally {
            if (previousJavaVersion == null) {
                System.clearProperty("java.version");
            } else {
                System.setProperty("java.version", previousJavaVersion);
            }
        }
    }
}
