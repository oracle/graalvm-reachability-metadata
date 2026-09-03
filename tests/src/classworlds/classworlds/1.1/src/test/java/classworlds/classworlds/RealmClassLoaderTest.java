/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package classworlds.classworlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.junit.jupiter.api.Test;

public class RealmClassLoaderTest {
    @Test
    void getResourceSearchesRealmClassLoaderDirectlyWhenResourceIsNotImported() throws Exception {
        ClassRealm realm = new ClassWorld().newRealm("direct-resource-realm");

        URL resource = realm.getResource("/resource/not-present-in-direct-realm.txt");

        assertThat(resource).isNull();
    }
}
