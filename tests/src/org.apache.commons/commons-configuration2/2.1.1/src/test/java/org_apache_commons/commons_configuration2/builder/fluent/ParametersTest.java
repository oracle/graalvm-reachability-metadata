/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.builder.fluent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.commons.configuration2.builder.FileBasedBuilderParametersImpl;
import org.apache.commons.configuration2.builder.fluent.FileBasedBuilderParameters;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.junit.jupiter.api.Test;

public class ParametersTest {
    @Test
    void createsFileBasedParametersProxyForFluentBuilderProperties() {
        final Parameters parametersFactory = new Parameters();
        final FileBasedBuilderParameters fileBasedParameters =
                parametersFactory.fileBased();

        final FileBasedBuilderParameters fluentResult = fileBasedParameters
                .setThrowExceptionOnMissing(true)
                .setEncoding("UTF-8")
                .setFileName("application.properties");
        final Map<String, Object> parameterMap =
                fileBasedParameters.getParameters();
        final FileBasedBuilderParametersImpl fileBasedDelegate =
                FileBasedBuilderParametersImpl.fromParameters(parameterMap);

        assertThat(fluentResult).isSameAs(fileBasedParameters);
        assertThat(parameterMap).containsEntry("throwExceptionOnMissing", true);
        assertThat(fileBasedDelegate).isNotNull();
        assertThat(fileBasedDelegate.getFileHandler().getEncoding())
                .isEqualTo("UTF-8");
        assertThat(fileBasedDelegate.getFileHandler().getFileName())
                .isEqualTo("application.properties");
    }

    @Test
    void appliesRegisteredDefaultsToNewProxyParameters() {
        final Parameters parametersFactory = new Parameters();
        parametersFactory.registerDefaultsHandler(FileBasedBuilderParameters.class,
                fileBasedParameters -> fileBasedParameters
                        .setBasePath("config")
                        .setFileName("defaults.xml"));

        final FileBasedBuilderParameters fileBasedParameters =
                parametersFactory.fileBased();
        final FileBasedBuilderParametersImpl fileBasedDelegate =
                FileBasedBuilderParametersImpl.fromParameters(
                        fileBasedParameters.getParameters());

        assertThat(fileBasedDelegate).isNotNull();
        assertThat(fileBasedDelegate.getFileHandler().getBasePath())
                .isEqualTo("config");
        assertThat(fileBasedDelegate.getFileHandler().getFileName())
                .isEqualTo("defaults.xml");
    }
}
