/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.aspectj.asm.AsmManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AsmManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndRestoresStructureModelFromConfigurationPath() throws IOException {
        boolean originalForceSingletonBehaviour = AsmManager.forceSingletonBehaviour;
        AsmManager originalLastActiveStructureModel = AsmManager.lastActiveStructureModel;
        try {
            AsmManager.forceSingletonBehaviour = false;
            AsmManager manager = newStructureModel();
            manager.getHierarchy().setConfigFile("compiler-config.lst");
            Path configurationPath = temporaryDirectory.resolve("project.lst");

            manager.writeStructureModel(configurationPath.toString());

            Path serializedModel = temporaryDirectory.resolve("project.ajsym");
            assertThat(serializedModel).isRegularFile();
            assertThat(Files.size(serializedModel)).isPositive();

            AsmManager restoredManager = newStructureModel();
            restoredManager.readStructureModel(configurationPath.toString());

            assertThat(restoredManager.getHierarchy()).isNotSameAs(manager.getHierarchy());
            assertThat(restoredManager.getHierarchy().getConfigFile()).isEqualTo("compiler-config.lst");
            assertThat(restoredManager.getRelationshipMap()).isNotSameAs(manager.getRelationshipMap());
            assertThat(restoredManager.getRelationshipMap().getEntries()).isEmpty();
        } finally {
            AsmManager.forceSingletonBehaviour = originalForceSingletonBehaviour;
            AsmManager.lastActiveStructureModel = originalLastActiveStructureModel;
        }
    }

    private static AsmManager newStructureModel() {
        return AsmManager.createNewStructureModel(Collections.<File, String>emptyMap());
    }
}
