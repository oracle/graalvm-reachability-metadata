/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginProvider;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.boot.origin.TextResourceOrigin.Location;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

public class OriginProviderArrayBindingTest {
    @Test
    void bindsAnArrayOfOriginProvidersAndRetainsTheirOrigins() {
        Origin first = new TextResourceOrigin(new ClassPathResource("orders.properties"), new Location(0, 0));
        Origin second = new TextResourceOrigin(new ClassPathResource("inventory.properties"), new Location(2, 4));
        OriginProvider firstProvider = () -> first;
        OriginProvider secondProvider = () -> second;
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "origins[0]", firstProvider, "origins[1]", secondProvider)));

        OriginProvider[] origins = binder.bind("origins", OriginProvider[].class).get();

        assertThat(origins).containsExactly(firstProvider, secondProvider);
        assertThat(Origin.from(origins[0])).isSameAs(first);
        assertThat(Origin.from(origins[1])).isSameAs(second);
    }
}
