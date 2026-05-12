/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_compiler_manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.fail;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.compiler.Compiler;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerError;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerOutputStyle;
import org.codehaus.plexus.compiler.CompilerResult;
import org.codehaus.plexus.compiler.javac.JavacCompiler;
import org.codehaus.plexus.compiler.manager.CompilerManager;
import org.codehaus.plexus.compiler.manager.DefaultCompilerManager;
import org.codehaus.plexus.compiler.manager.NoSuchCompilerException;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Plexus_compiler_managerTest {
    private static final String COMPONENT_DESCRIPTOR = "META-INF/plexus/components.xml";

    @Test
    public void plexusDescriptorAdvertisesCompilerManagerComponent() throws Exception {
        Element component = findCompilerManagerComponent();

        assertThat(directChildText(component, "role")).isEqualTo(CompilerManager.ROLE);
        assertThat(directChildText(component, "role-hint")).isEqualTo("default");
        assertThat(directChildText(component, "implementation"))
                .isEqualTo(DefaultCompilerManager.class.getName());
        assertThat(directChildText(component, "isolated-realm")).isEqualTo("false");

        Node requirementNode = directChild(component, "requirements").getElementsByTagName("requirement").item(0);
        assertThat(requirementNode).isInstanceOf(Element.class);
        Element requirement = (Element) requirementNode;
        assertThat(directChildText(requirement, "role")).isEqualTo(Compiler.ROLE);
        assertThat(directChildText(requirement, "field-name")).isEqualTo("compilers");
    }

    @Test
    public void containerLooksUpManagerAndManagerReturnsDiscoveredJavacCompiler() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            assertThat(container.hasComponent(CompilerManager.class)).isTrue();
            assertThat(container.lookup(CompilerManager.ROLE)).isInstanceOf(DefaultCompilerManager.class);

            CompilerManager manager = container.lookup(CompilerManager.class);
            Compiler compiler = manager.getCompiler("javac");

            assertThat(compiler).isInstanceOf(JavacCompiler.class);
            assertThat(compiler.getCompilerOutputStyle()).isEqualTo(CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE);

            CompilerConfiguration configuration = new CompilerConfiguration();
            assertThat(compiler.getInputFileEnding(configuration)).isEqualTo(".java");
            assertThat(compiler.getOutputFileEnding(configuration)).isEqualTo(".class");
            assertThat(compiler.canUpdateTarget(configuration)).isTrue();
        } finally {
            container.dispose();
        }
    }

    @Test
    public void managerReportsRequestedHintWhenCompilerIsMissing() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            CompilerManager manager = container.lookup(CompilerManager.class);
            String compilerId = "missing-compiler";

            NoSuchCompilerException exception = catchThrowableOfType(
                    () -> manager.getCompiler(compilerId), NoSuchCompilerException.class);

            assertThat(exception).isNotNull();
            assertThat(exception).hasMessage("No such compiler 'missing-compiler'.");
            assertThat(exception.getCompilerId()).isEqualTo(compilerId);
        } finally {
            container.dispose();
        }
    }

    @Test
    public void managerReturnsCompilerRegisteredWithAdditionalRoleHint() throws Exception {
        DefaultPlexusContainer container = new DefaultPlexusContainer();
        try {
            String compilerId = "in-memory";
            ComponentDescriptor<Compiler> descriptor = new ComponentDescriptor<>();
            descriptor.setRole(Compiler.ROLE);
            descriptor.setRoleClass(Compiler.class);
            descriptor.setRoleHint(compilerId);
            descriptor.setImplementationClass(InMemoryCompiler.class);
            container.addComponentDescriptor(descriptor);

            CompilerManager manager = container.lookup(CompilerManager.class);
            Compiler compiler = manager.getCompiler(compilerId);

            assertThat(compiler).isInstanceOf(InMemoryCompiler.class);
            assertThat(compiler.getCompilerOutputStyle()).isEqualTo(CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES);
            assertThat(compiler.getInputFileEnding(new CompilerConfiguration())).isEqualTo(".source");
            assertThat(compiler.performCompile(new CompilerConfiguration()).isSuccess()).isTrue();
        } finally {
            container.dispose();
        }
    }

    private static Element findCompilerManagerComponent() throws Exception {
        Enumeration<URL> descriptors = Thread.currentThread().getContextClassLoader()
                .getResources(COMPONENT_DESCRIPTOR);
        while (descriptors.hasMoreElements()) {
            URL descriptor = descriptors.nextElement();
            try (InputStream stream = descriptor.openStream()) {
                Document document = newDocumentBuilderFactory().newDocumentBuilder().parse(stream);
                NodeList components = document.getElementsByTagName("component");
                for (int i = 0; i < components.getLength(); i++) {
                    Node node = components.item(i);
                    if (node instanceof Element component
                            && CompilerManager.ROLE.equals(directChildText(component, "role"))) {
                        return component;
                    }
                }
            }
        }
        return fail("Could not find the compiler manager Plexus component descriptor");
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(false);
        return factory;
    }

    private static Element directChild(Element element, String name) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && name.equals(childElement.getTagName())) {
                return childElement;
            }
        }
        return fail("Could not find child element: " + name);
    }

    private static String directChildText(Element element, String name) {
        return directChild(element, name).getTextContent().trim();
    }

    public static final class InMemoryCompiler implements Compiler {
        @Override
        public CompilerOutputStyle getCompilerOutputStyle() {
            return CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES;
        }

        @Override
        public String getInputFileEnding(CompilerConfiguration compilerConfiguration) throws CompilerException {
            return ".source";
        }

        @Override
        public String getOutputFileEnding(CompilerConfiguration compilerConfiguration) throws CompilerException {
            return ".compiled";
        }

        @Override
        public String getOutputFile(CompilerConfiguration compilerConfiguration) throws CompilerException {
            return "in-memory-output";
        }

        @Override
        public boolean canUpdateTarget(CompilerConfiguration compilerConfiguration) throws CompilerException {
            return false;
        }

        @Override
        public CompilerResult performCompile(CompilerConfiguration compilerConfiguration) throws CompilerException {
            return new CompilerResult(true, Collections.emptyList());
        }

        @Override
        public List<CompilerError> compile(CompilerConfiguration compilerConfiguration) throws CompilerException {
            return Collections.emptyList();
        }

        @Override
        public String[] createCommandLine(CompilerConfiguration compilerConfiguration) throws CompilerException {
            return new String[] {"in-memory-compiler"};
        }
    }
}
