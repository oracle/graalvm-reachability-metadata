/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.strategy.DefaultStrategy;
import org.junit.jupiter.api.Test;

public class DefaultStrategyTest {
    private static final String REALM_ID = "default-realm";

    @Test
    void loadClassDelegatesClassworldsClassesToClassworldsClassLoader() throws Exception {
        ClassRealm realm = new ClassWorld().newRealm(REALM_ID);

        Class<?> loadedClass = realm.loadClass(ClassWorld.class.getName());

        assertThat(realm.getStrategy()).isInstanceOf(DefaultStrategy.class);
        assertThat(loadedClass).isSameAs(ClassWorld.class);
    }
}
