/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model_builder;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.superpom.DefaultSuperPomProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSuperPomProviderTest {
    @Test
    void loadsAndCachesMavenFourSuperPomResource() {
        DefaultModelProcessor modelProcessor = new DefaultModelProcessor()
                .setModelReader(new DefaultModelReader());
        DefaultSuperPomProvider provider = new DefaultSuperPomProvider()
                .setModelProcessor(modelProcessor);

        Model superModel = provider.getSuperModel("4.0.0");
        Model cachedSuperModel = provider.getSuperModel("4.0.0");

        assertThat(superModel).isSameAs(cachedSuperModel);
        assertThat(superModel.getModelVersion()).isEqualTo("4.0.0");
        assertThat(superModel.getRepositories())
                .extracting("id")
                .containsExactly("central");
        assertThat(superModel.getPluginRepositories())
                .extracting("id")
                .containsExactly("central");
        assertThat(superModel.getBuild().getDirectory()).isEqualTo("${project.basedir}/target");
    }
}
