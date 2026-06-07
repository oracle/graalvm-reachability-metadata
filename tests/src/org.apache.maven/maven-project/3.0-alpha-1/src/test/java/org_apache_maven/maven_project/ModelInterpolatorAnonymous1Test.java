/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_project;

import org.apache.maven.project.builder.Interpolator;
import org.apache.maven.shared.model.InterpolatorProperty;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelInterpolatorAnonymous1Test {
    @Test
    void interpolatesProjectPropertiesFromXmlModel() throws Exception {
        String pomXml = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>demo-artifact</artifactId>
                  <version>${custom.version}</version>
                  <properties>
                    <custom.version>1.2.3</custom.version>
                  </properties>
                </project>
                """;
        List<InterpolatorProperty> properties = Collections.emptyList();

        String interpolatedXml = Interpolator.interpolateXmlString(pomXml, properties);

        assertThat(interpolatedXml).contains("1.2.3");
        assertThat(interpolatedXml).doesNotContain("${custom.version}");
    }
}
