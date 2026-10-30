/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.file_management;

import org.apache.maven.shared.model.fileset.Mapper;
import org.apache.maven.shared.model.fileset.mappers.FileNameMapper;
import org.apache.maven.shared.model.fileset.mappers.MapperUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapperUtilTest {
    @Test
    void defaultMapperPreservesFileNames() throws Exception {
        Mapper configuration = new Mapper();

        FileNameMapper mapper = MapperUtil.getFileNameMapper(configuration);

        assertThat(mapper.mapFileName("report.txt")).isEqualTo("report.txt");
        assertThat(mapper.mapFileName("archive.tar.gz")).isEqualTo("archive.tar.gz");
    }

    @Test
    void builtInGlobMapperUsesConfiguredPatterns() throws Exception {
        Mapper configuration = new Mapper();
        configuration.setType("glob");
        configuration.setFrom("input-*.txt");
        configuration.setTo("output-*.csv");

        FileNameMapper mapper = MapperUtil.getFileNameMapper(configuration);

        assertThat(mapper.mapFileName("input-orders.txt")).isEqualTo("output-orders.csv");
        assertThat(mapper.mapFileName("input-customers.txt")).isEqualTo("output-customers.csv");
        assertThat(mapper.mapFileName("notes.txt")).isNull();
        assertThat(mapper.mapFileName("input-orders.xml")).isNull();
    }

    @Test
    void explicitlyNamedMapperUsesConfiguredDestination() throws Exception {
        Mapper configuration = new Mapper();
        configuration.setType(null);
        configuration.setClassname("org.apache.maven.shared.model.fileset.mappers.MergingMapper");
        configuration.setTo("combined.txt");

        FileNameMapper mapper = MapperUtil.getFileNameMapper(configuration);

        assertThat(mapper.mapFileName("first.txt")).isEqualTo("combined.txt");
        assertThat(mapper.mapFileName("second.txt")).isEqualTo("combined.txt");
    }
}
