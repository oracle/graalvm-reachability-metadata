/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_codegen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.querydsl.codegen.GenericExporter;
import com.querydsl.core.annotations.QueryProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericExporterTest {

    @TempDir
    Path targetFolder;

    @Test
    void exportProjectionTypeWithConstructorFieldsAndAccessors() throws Exception {
        GenericExporter exporter = new GenericExporter(GenericExporterTest.class.getClassLoader());
        exporter.setTargetFolder(targetFolder.toFile());

        exporter.export(ProjectionDto.class);

        Set<File> generatedFiles = exporter.getGeneratedFiles();
        assertThat(generatedFiles).hasSize(1);
        File generatedFile = generatedFiles.iterator().next();
        assertThat(generatedFile).exists();

        String generatedSource = Files.readString(generatedFile.toPath());
        assertThat(generatedSource)
                .contains("class QGenericExporterTest_ProjectionDto")
                .contains("extends ConstructorExpression")
                .contains("param0")
                .contains("param1")
                .contains("param2");
    }

    public static class ProjectionDto {

        private final String firstName;
        private final int rank;
        private final boolean active;

        @QueryProjection
        public ProjectionDto(String firstName, int rank, boolean active) {
            this.firstName = firstName;
            this.rank = rank;
            this.active = active;
        }

        public String getDisplayName() {
            return firstName + "#" + rank;
        }

        public boolean isActive() {
            return active;
        }
    }
}
