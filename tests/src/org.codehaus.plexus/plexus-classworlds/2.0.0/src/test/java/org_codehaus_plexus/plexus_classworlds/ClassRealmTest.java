/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Enumeration;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.junit.jupiter.api.Test;

public class ClassRealmTest {
    private static final String REALM_ID = "direct-realm";

    @Test
    void loadClassLoadsClassThroughRealmClassLoader() throws Exception {
        ClassRealm realm = newRealm();

        Class<?> loadedClass = realm.loadClass(ClassRealmTest.class.getName());

        assertThat(loadedClass).isSameAs(ClassRealmTest.class);
    }

    @Test
    void getResourceLoadsResourceThroughRealmClassLoader() throws Exception {
        ClassRealm realm = newRealm();

        URL resource = realm.getResource("classworlds.conf");

        assertThat(resource).isNotNull();
    }

    @Test
    void loadResourceFromParentUsesForeignClassLoader() throws Exception {
        ClassRealm realm = newRealm();

        URL resource = realm.loadResourceFromParent("classworlds.conf");

        assertThat(resource).isNotNull();
    }

    @Test
    void loadResourcesFromParentUsesForeignClassLoader() throws Exception {
        ClassRealm realm = newRealm();

        Enumeration<?> resources = realm.loadResourcesFromParent("classworlds.conf");

        assertThat(resources).isNotNull();
        assertThat(resources.hasMoreElements()).isTrue();
    }

    private static ClassRealm newRealm() throws Exception {
        ClassWorld world = new ClassWorld();
        return world.newRealm(REALM_ID, ClassRealmTest.class.getClassLoader());
    }
}
