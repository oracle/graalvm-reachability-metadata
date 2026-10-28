/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypePoolInnerClassLoadingTest {
    @Test
    void describesExistingApplicationTypeByName() {
        TypePool pool = TypePool.ClassLoading.of(getClass().getClassLoader());
        TypeDescription type = pool.describe(TestFixtures.Sample.class.getName()).resolve();
        assertTrue(type.represents(TestFixtures.Sample.class));
        assertTrue(type.getDeclaredFields().filter(named("title")).getOnly().getType().asErasure().represents(String.class));
        assertTrue(pool.describe("[Ljava.lang.String;").resolve().represents(String[].class));
        pool.clear();
        assertTrue(pool.describe(TestFixtures.Parent.class.getName()).resolve().represents(TestFixtures.Parent.class));
    }
}
