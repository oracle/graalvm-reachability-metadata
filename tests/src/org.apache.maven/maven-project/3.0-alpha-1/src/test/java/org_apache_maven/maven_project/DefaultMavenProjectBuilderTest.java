/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_project;

import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultMavenProjectBuilderTest {
    @Test
    void buildStandaloneSuperProjectLoadsBundledSuperPomResource() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            PlexusContainer plexusContainer = container;
            MavenProjectBuilder builder = (MavenProjectBuilder) plexusContainer.lookup(
                    MavenProjectBuilder.class.getName());

            MavenProject project = builder.buildStandaloneSuperProject(new DefaultProjectBuilderConfiguration());

            assertThat(project.isExecutionRoot()).isTrue();
            assertThat(project.getModel().getModelVersion()).isEqualTo("4.0.0");
            assertThat(project.getModel().getBuild().getSourceDirectory())
                    .isEqualTo("${project.basedir}/src/main/java");
        } finally {
            container.dispose();
        }
    }
}
