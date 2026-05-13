/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.jupiter.api.Test;

public class DefaultPlexusContainerTest {
    private static final String PLEXUS_XML_RESOURCE =
        "/org_sonatype_sisu/sisu_inject_plexus/default-plexus-container-test.xml";

    @Test
    void addsComponentUsingRoleName() throws PlexusContainerException, ComponentLookupException {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            TestService component = new TestServiceImpl("registered through role name");

            container.addComponent(component, TestService.class.getName());

            assertThat(container.lookup(TestService.class).message()).isEqualTo("registered through role name");
        } finally {
            container.dispose();
        }
    }

    @Test
    void loadsContainerConfigurationFromClasspathResource()
        throws PlexusContainerException, ComponentLookupException {
        DefaultContainerConfiguration configuration = new DefaultContainerConfiguration();
        configuration.setContainerConfiguration(PLEXUS_XML_RESOURCE);

        DefaultPlexusContainer container = new DefaultPlexusContainer(configuration);
        try {
            XmlConfiguredService service = container.lookup(XmlConfiguredService.class);

            assertThat(service.message()).isEqualTo("configured from plexus xml");
        } finally {
            container.dispose();
        }
    }

    public interface TestService {
        String message();
    }

    public static final class TestServiceImpl implements TestService {
        private final String message;

        TestServiceImpl(final String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    public interface XmlConfiguredService {
        String message();
    }

    public static final class XmlConfiguredServiceImpl implements XmlConfiguredService {
        @Override
        public String message() {
            return "configured from plexus xml";
        }
    }
}
