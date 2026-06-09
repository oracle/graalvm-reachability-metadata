/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.junit.jupiter.api.Test;

public class ClassRealmAdapterTest {
    @Test
    void getResourceDelegatesThroughRealmClassLoader() throws Exception {
        ClassWorld world = new ClassWorld();
        ClassRealm realm = world.newRealm("adapter-realm", ClassRealmAdapterTest.class.getClassLoader());

        URL resource = realm.getResource("classworlds.conf");

        assertThat(resource).isNotNull();
    }
}
