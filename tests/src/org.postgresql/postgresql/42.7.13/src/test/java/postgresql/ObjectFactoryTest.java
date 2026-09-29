/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.util.Properties;

import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;
import org.postgresql.geometric.PGpoint;
import org.postgresql.ssl.DefaultJavaSSLFactory;
import org.postgresql.util.ObjectFactory;
import org.postgresql.util.PGInterval;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.util.ObjectFactory}.
 */
public class ObjectFactoryTest {

    @Test
    void instantiatePrefersPropertiesConstructorWhenAvailable() throws Exception {
        Properties properties = new Properties();

        SSLSocketFactory factory = ObjectFactory.instantiate(SSLSocketFactory.class, DefaultJavaSSLFactory.class.getName(),
                properties, true, "ignored");

        assertThat(factory).isInstanceOf(DefaultJavaSSLFactory.class);
    }

    @Test
    void instantiateFallsBackToStringConstructorWhenRequested() throws Exception {
        Properties properties = new Properties();

        PGInterval interval = ObjectFactory.instantiate(PGInterval.class, PGInterval.class.getName(), properties, true,
                "1 year 2 mons 3 days 04:05:06");

        assertThat(interval.getYears()).isEqualTo(1);
        assertThat(interval.getMonths()).isEqualTo(2);
        assertThat(interval.getDays()).isEqualTo(3);
        assertThat(interval.getHours()).isEqualTo(4);
        assertThat(interval.getMinutes()).isEqualTo(5);
        assertThat(interval.getSeconds()).isEqualTo(6.0);
    }

    @Test
    void instantiateFallsBackToNoArgumentConstructor() throws Exception {
        Properties properties = new Properties();

        PGpoint point = ObjectFactory.instantiate(PGpoint.class, PGpoint.class.getName(), properties, false, null);

        assertThat(point.x).isZero();
        assertThat(point.y).isZero();
    }
}
