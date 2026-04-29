/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package android.os;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.strategy.PlatformDescription;

public class PlatformDescriptionTest {
    private static final String GET_ANDROID_VERSION_METHOD = "getAndroidVersion0";

    @Test
    void detectsLegacyAndroidApiLevelsUsingTheSdkFallbackField() throws Exception {
        assertThat(Build.VERSION.SDK).isEqualTo("3");

        Method getAndroidVersion = PlatformDescription.class.getDeclaredMethod(GET_ANDROID_VERSION_METHOD);
        getAndroidVersion.setAccessible(true);

        int androidVersion = (Integer) getAndroidVersion.invoke(null);

        assertThat(androidVersion).isEqualTo(3);
    }
}

final class Build {
    private Build() {
    }

    public static final class VERSION {
        public static String SDK = "3";

        private VERSION() {
        }
    }
}
