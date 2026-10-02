/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.asm.AsmManager;
import org.aspectj.asm.IHierarchy;
import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IRelationship;
import org.aspectj.asm.internal.ProgramElement;
import org.aspectj.asm.internal.Relationship;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AsmManagerTest {
    private static final String SOURCE_HANDLE = "sample-project/src/Sample.java";
    private static final String TARGET_HANDLE = "sample-project/src/TracingAspect.aj";

    @Test
    void serializesAndRestoresStructureModel() throws Exception {
        Path workDirectory = Files.createTempDirectory("aspectj-asm-manager");
        Path configFile = workDirectory.resolve("sample.lst");
        Path sourceFile = workDirectory.resolve("src").resolve("Sample.java");
        Path serializedModel = workDirectory.resolve("sample.ajsym");

        AsmManager original = AsmManager.createNewStructureModel(inpathMap(workDirectory));
        installHierarchy(original, configFile, sourceFile);
        installRelationship(original);

        original.writeStructureModel(configFile.toString());

        AsmManager restored = AsmManager.createNewStructureModel(inpathMap(workDirectory));
        List<IHierarchy> updateNotifications = new ArrayList<>();
        restored.addListener(updateNotifications::add);
        restored.readStructureModel(configFile.toString());

        assertThat(serializedModel).exists();
        assertThat(Files.size(serializedModel)).isGreaterThan(0L);
        assertThat(updateNotifications).hasSize(1);
        assertThat(restored.getHierarchy().isValid()).isTrue();
        assertThat(restored.getHierarchy().getConfigFile()).isEqualTo(configFile.toString());
        assertThat(restored.getHierarchy().getRoot().getName()).isEqualTo("sample-project");
        assertThat(restored.getHierarchy().getRoot().getChildren())
                .extracting(IProgramElement::getHandleIdentifier)
                .containsExactly(SOURCE_HANDLE);
        assertThat(((IProgramElement) restored.getHierarchy().findInFileMap(sourceFile.toString())).getName())
                .isEqualTo("Sample.java");
        assertThat(restored.getRelationshipMap().get(SOURCE_HANDLE)).singleElement().satisfies(relationship -> {
            assertThat(relationship.getKind()).isEqualTo(IRelationship.Kind.ADVICE);
            assertThat(relationship.hasRuntimeTest()).isTrue();
            assertThat(relationship.getTargets()).containsExactly(TARGET_HANDLE);
        });
    }

    private static Map<File, String> inpathMap(Path workDirectory) {
        Map<File, String> inpathMap = new HashMap<>();
        inpathMap.put(workDirectory.toFile(), "sample-project");
        return inpathMap;
    }

    private static void installHierarchy(AsmManager manager, Path configFile, Path sourceFile) {
        IProgramElement project = new ProgramElement(
                manager,
                "sample-project",
                IProgramElement.Kind.PROJECT,
                new ArrayList<>()
        );
        IProgramElement source = new ProgramElement(
                manager,
                "Sample.java",
                IProgramElement.Kind.FILE_JAVA,
                new ArrayList<>()
        );
        source.setHandleIdentifier(SOURCE_HANDLE);
        project.addChild(source);

        Map<String, IProgramElement> fileMap = new HashMap<>();
        fileMap.put(sourceFile.toString(), source);
        manager.getHierarchy().setConfigFile(configFile.toString());
        manager.getHierarchy().setRoot(project);
        manager.getHierarchy().setFileMap(fileMap);
    }

    private static void installRelationship(AsmManager manager) {
        IRelationship relationship = new Relationship(
                "advises",
                IRelationship.Kind.ADVICE,
                SOURCE_HANDLE,
                new ArrayList<>(),
                true
        );
        relationship.addTarget(TARGET_HANDLE);
        manager.getRelationshipMap().put(SOURCE_HANDLE, relationship);
    }
}
