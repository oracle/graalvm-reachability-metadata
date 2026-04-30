/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite.objects;

import net.razorvine.pickle.objects.Reconstructor;
import net.razorvine.pickle.objects.TimeZoneConstructor;
import net.razorvine.pickle.objects.Tzinfo;
import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

public class ReconstructorTest {
    @Test
    void delegatesReconstructionToPythonSubclassConstructor() {
        Reconstructor constructor = new Reconstructor();
        TimeZoneConstructor subclassConstructor = new TimeZoneConstructor(TimeZoneConstructor.DATEUTIL_TZUTC);
        TimeZoneConstructor baseConstructor = new TimeZoneConstructor(TimeZoneConstructor.TZINFO);
        Tzinfo state = new Tzinfo();

        Object value = constructor.construct(new Object[]{subclassConstructor, baseConstructor, state});

        assertThat(value).isInstanceOfSatisfying(TimeZone.class, timeZone -> {
            assertThat(timeZone.getID()).isEqualTo("UTC");
        });
    }
}
