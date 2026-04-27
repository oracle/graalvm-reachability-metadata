/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import org.apache.log4j.spi.LocationInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LocationInfoTest {

    @Test
    void resolvesCallerDetailsFromThrowableStackTrace() {
        LocationInfo locationInfo = captureLocationInfo();

        String expectedFullInfo = LocationInfoTest.class.getName()
                + ".captureLocationInfo(LocationInfoTest.java:"
                + locationInfo.getLineNumber()
                + ")";

        assertThat(locationInfo.fullInfo).isEqualTo(expectedFullInfo);
        assertThat(locationInfo.getClassName()).isEqualTo(LocationInfoTest.class.getName());
        assertThat(locationInfo.getMethodName()).isEqualTo("captureLocationInfo");
        assertThat(locationInfo.getFileName()).isEqualTo("LocationInfoTest.java");
        assertThat(locationInfo.getLineNumber()).matches("\\d+");
    }

    private static LocationInfo captureLocationInfo() {
        return LoggingBoundary.captureLocation();
    }

    private static final class LoggingBoundary {
        private static LocationInfo captureLocation() {
            return new LocationInfo(new Throwable(), LoggingBoundary.class.getName());
        }
    }
}
