/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc;

import com.sun.codemodel.JCodeModel;
import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.api.S2JJAXBModel;
import com.sun.tools.xjc.api.SchemaCompiler;
import com.sun.tools.xjc.api.XJC;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XjcTestSupport {
    private XjcTestSupport() {
    }

    static String schema(String body) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb" jaxb:version="3.0">
                %s
                </xs:schema>
                """.formatted(body);
    }

    static InputSource input(String name, String text) {
        InputSource input = new InputSource(new StringReader(text));
        input.setSystemId("file:/xjc-input/" + name);
        return input;
    }

    static SchemaCompiler compiler(Diagnostics diagnostics) {
        SchemaCompiler compiler = XJC.createSchemaCompiler();
        compiler.forcePackageName("generated");
        compiler.setErrorListener(diagnostics);
        return compiler;
    }

    static JCodeModel compile(String schema, Diagnostics diagnostics, Plugin... plugins) {
        SchemaCompiler compiler = compiler(diagnostics);
        for (Plugin plugin : plugins) {
            compiler.getOptions().activePlugins.add(plugin);
        }
        compiler.parseSchema(input("schema.xsd", schema));
        S2JJAXBModel model = compiler.bind();
        assertNotNull(model, diagnostics.errors.toString());
        assertTrue(diagnostics.errors.isEmpty(), diagnostics.errors.toString());
        return model.generateCode(null, diagnostics);
    }

    static String source(JCodeModel model, Path directory, String className) throws IOException {
        model.build(directory.toFile());
        return Files.readString(directory.resolve("generated").resolve(className + ".java"));
    }

    static final class Diagnostics extends ErrorReceiver {
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> information = new ArrayList<>();

        @Override
        public void error(SAXParseException exception) {
            errors.add(exception.getMessage());
        }

        @Override
        public void fatalError(SAXParseException exception) {
            errors.add(exception.getMessage());
        }

        @Override
        public void warning(SAXParseException exception) {
            warnings.add(exception.getMessage());
        }

        @Override
        public void info(SAXParseException exception) {
            information.add(exception.getMessage());
        }
    }
}
