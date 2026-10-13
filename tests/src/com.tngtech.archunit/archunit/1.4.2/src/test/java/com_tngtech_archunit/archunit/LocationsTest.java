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
    void locatesThePackageAndClassResourcesOnTheApplicationClassPath() {
        Set<Location> packageLocations = Locations.ofPackage(getClass().getPackageName());
        Set<Location> classLocations = Locations.ofClass(AnnotatedFixture.class);

        assertThat(packageLocations).isNotEmpty();
        assertThat(classLocations).isNotEmpty();
        assertThat(classLocations)
                .anySatisfy(location -> assertThat(location.toString())
                        .contains("AnnotatedFixture.class"));
    }
}
