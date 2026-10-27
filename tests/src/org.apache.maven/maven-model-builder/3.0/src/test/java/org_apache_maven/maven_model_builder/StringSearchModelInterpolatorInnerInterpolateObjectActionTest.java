/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model_builder;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.StringModelSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringSearchModelInterpolatorInnerInterpolateObjectActionTest {
    @Test
    void buildsModelWithInterpolatedStringsCollectionsMapsAndNestedObjects() throws Exception {
        String pom = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>demo-artifact</artifactId>
                  <version>1.0</version>
                  <packaging>pom</packaging>
                  <name>${project.artifactId}</name>
                  <description>${project.artifactId} description</description>
                  <organization>
                    <name>${project.artifactId} organization</name>
                    <url>https://example.invalid/${project.artifactId}</url>
                  </organization>
                  <modules>
                    <module>${module.name}</module>
                  </modules>
                  <properties>
                    <module.name>module-a</module.name>
                    <expanded.property>${project.artifactId}</expanded.property>
                  </properties>
                </project>
                """;

        ModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
        DefaultModelBuildingRequest request = new DefaultModelBuildingRequest()
                .setModelSource(new StringModelSource(pom, "memory-pom.xml"))
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        ModelBuildingResult result = builder.build(request);
        Model model = result.getEffectiveModel();

        assertThat(model.getName()).isEqualTo("demo-artifact");
        assertThat(model.getDescription()).isEqualTo("demo-artifact description");
        assertThat(model.getModules()).containsExactly("module-a");
        assertThat(model.getProperties().getProperty("expanded.property")).isEqualTo("demo-artifact");
        assertThat(model.getOrganization().getName()).isEqualTo("demo-artifact organization");
        assertThat(model.getOrganization().getUrl()).isEqualTo("https://example.invalid/demo-artifact");
    }
}
