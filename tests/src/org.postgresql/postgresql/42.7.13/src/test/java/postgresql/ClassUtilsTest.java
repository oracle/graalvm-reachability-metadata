/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import org.junit.jupiter.api.Test;
import org.postgresql.util.ClassUtils;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.util.ClassUtils}.
 */
public class ClassUtilsTest {

    @Test
    @SuppressWarnings("deprecation")
    void loadsNamedSubtypeWithSpecifiedClassLoader() throws Exception {
        Class<? extends PGobject> loadedClass = ClassUtils.forName(PGInterval.class.getName(), PGobject.class,
                ClassUtilsTest.class.getClassLoader());

        assertThat(loadedClass).isEqualTo(PGInterval.class);
    }
}
