/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag_maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.codehaus.plexus.PlexusContainer;
import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.DependencyFilter;
import io.smallrye.beanbag.maven.MavenFactory;

public class PlexusContainerImplTest {
    @Test
    void resolvesComponentsByStringRoleAndHint() throws Exception {
        TestComponents components = new TestComponents();
        PlexusContainer container = newPlexusContainer(components);
        String roleName = ComponentRole.class.getName();

        Object defaultComponent = container.lookup(roleName);
        Object secondaryComponent = container.lookup(roleName, "secondary");

        assertThat(defaultComponent).isSameAs(components.primaryComponent);
        assertThat(secondaryComponent).isSameAs(components.secondaryComponent);
    }

    @Test
    void resolvesListsAndMapsByStringRole() throws Exception {
        TestComponents components = new TestComponents();
        PlexusContainer container = newPlexusContainer(components);
        String roleName = ComponentRole.class.getName();

        List<Object> componentList = container.lookupList(roleName);
        Map<String, Object> componentMap = container.lookupMap(roleName);

        assertThat(componentList).containsExactly(
                components.primaryComponent,
                components.secondaryComponent);
        assertThat(componentMap)
                .containsEntry("primary", components.primaryComponent)
                .containsEntry("secondary", components.secondaryComponent);
    }

    @Test
    void detectsComponentsByStringRoleAndTypedStringRole() throws Exception {
        TestComponents components = new TestComponents();
        PlexusContainer container = newPlexusContainer(components);
        String roleName = ComponentRole.class.getName();

        assertThat(container.hasComponent(roleName)).isTrue();
        assertThat(container.hasComponent(roleName, "secondary")).isTrue();
        assertThat(container.hasComponent(
                SecondaryComponent.class,
                roleName,
                "secondary")).isTrue();
    }

    private static PlexusContainer newPlexusContainer(TestComponents components) {
        MavenFactory factory = MavenFactory.create(
                List.of(),
                builder -> builder
                        .addBean(PrimaryComponent.class)
                        .setName("primary")
                        .setPriority(10)
                        .setInstance(components.primaryComponent)
                        .build()
                        .addBean(SecondaryComponent.class)
                        .setName("secondary")
                        .setPriority(5)
                        .setInstance(components.secondaryComponent)
                        .build(),
                DependencyFilter.ACCEPT);
        return factory.getContainer().requireBean(PlexusContainer.class);
    }

    private interface ComponentRole {
        String value();
    }

    private static final class PrimaryComponent implements ComponentRole {
        @Override
        public String value() {
            return "primary";
        }
    }

    private static final class SecondaryComponent implements ComponentRole {
        @Override
        public String value() {
            return "secondary";
        }
    }

    private static final class TestComponents {
        private final PrimaryComponent primaryComponent = new PrimaryComponent();
        private final SecondaryComponent secondaryComponent = new SecondaryComponent();
    }
}
