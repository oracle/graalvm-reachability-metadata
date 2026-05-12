/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.file_management;

import org.apache.maven.shared.model.fileset.Mapper;
import org.apache.maven.shared.model.fileset.mappers.FileNameMapper;
import org.apache.maven.shared.model.fileset.mappers.FlatFileNameMapper;
import org.apache.maven.shared.model.fileset.mappers.IdentityMapper;
import org.apache.maven.shared.model.fileset.mappers.MapperUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapperUtilTest {
    @Test
    void createsBuiltInMapperFromClasspathProperties() throws Exception {
        Mapper mapper = new Mapper();
        mapper.setType("flatten");

        FileNameMapper fileNameMapper = MapperUtil.getFileNameMapper(mapper);

        assertThat(fileNameMapper).isInstanceOf(FlatFileNameMapper.class);
        assertThat(fileNameMapper.mapFileName("src/main/java/Example.java")).isEqualTo("Example.java");
    }

    @Test
    void createsCustomMapperFromConfiguredClassName() throws Exception {
        Mapper mapper = new Mapper();
        mapper.setType(null);
        mapper.setClassname(IdentityMapper.class.getName());

        FileNameMapper fileNameMapper = MapperUtil.getFileNameMapper(mapper);

        assertThat(fileNameMapper).isInstanceOf(IdentityMapper.class);
        assertThat(fileNameMapper.mapFileName("src/main/java/Example.java"))
            .isEqualTo("src/main/java/Example.java");
    }
}
