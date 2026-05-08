/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag_maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.codehaus.plexus.PlexusContainer;
import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.DependencyFilter;
import io.smallrye.beanbag.maven.MavenFactory;

public class PlexusContainerImplTest {
    private static final String DEFAULT_VALUE = "default-value";
    private static final String NAMED_VALUE = "named-value";
    private static final String NAMED_HINT = "named";

    @Test
    void stringBasedPlexusContainerOperationsResolveBeans() throws Exception {
        PlexusContainer container = createContainer();

        assertThat(container.lookup(String.class.getName())).isEqualTo(DEFAULT_VALUE);
        assertThat(container.lookup(String.class.getName(), NAMED_HINT)).isEqualTo(NAMED_VALUE);

        assertThat(container.lookupList(String.class.getName()))
                .contains(DEFAULT_VALUE, NAMED_VALUE);

        assertThat(container.lookupMap(String.class.getName()))
                .containsEntry("", DEFAULT_VALUE)
                .containsEntry(NAMED_HINT, NAMED_VALUE);

        assertThat(container.hasComponent(String.class.getName())).isTrue();
        assertThat(container.hasComponent(String.class.getName(), NAMED_HINT)).isTrue();
        assertThat(container.hasComponent(String.class, String.class.getName(), NAMED_HINT)).isTrue();
    }

    private static PlexusContainer createContainer() {
        MavenFactory factory = MavenFactory.create(List.of(), builder -> {
            builder.addBean(String.class)
                    .setPriority(100)
                    .setInstance(DEFAULT_VALUE)
                    .build();
            builder.addBean(String.class)
                    .setName(NAMED_HINT)
                    .setInstance(NAMED_VALUE)
                    .build();
        }, DependencyFilter.ACCEPT);
        return factory.getContainer().requireBean(PlexusContainer.class);
    }
}
