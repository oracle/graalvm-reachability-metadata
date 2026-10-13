/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.importer.Locations;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class LocationsTest {
    @Test
    void locatesClassResourcesThroughTheApplicationClassLoader() {
        Set<Location> locations = Locations.ofClass(AnnotatedFixture.class);

        assertThat(locations).isNotEmpty();
        assertThat(locations)
                .anySatisfy(location -> assertThat(location.toString())
                        .contains("AnnotatedFixture.class"));
    }
}
