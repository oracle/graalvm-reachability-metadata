/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.sundr.deps.org.apache.velocity.VelocityContext;
import io.sundr.deps.org.apache.velocity.app.VelocityEngine;
import io.sundr.deps.org.apache.velocity.context.InternalContextAdapter;
import io.sundr.deps.org.apache.velocity.exception.MethodInvocationException;
import io.sundr.deps.org.apache.velocity.exception.ParseErrorException;
import io.sundr.deps.org.apache.velocity.exception.ResourceNotFoundException;
import io.sundr.deps.org.apache.velocity.exception.TemplateInitException;
import io.sundr.deps.org.apache.velocity.exception.VelocityException;
import io.sundr.deps.org.apache.velocity.runtime.ParserPoolImpl;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeConstants;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeInstance;
import io.sundr.deps.org.apache.velocity.runtime.directive.Break;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.runtime.parser.Token;
import io.sundr.deps.org.apache.velocity.runtime.parser.node.Node;
import io.sundr.deps.org.apache.velocity.runtime.parser.node.ParserVisitor;
import io.sundr.deps.org.apache.velocity.runtime.resource.ResourceCacheImpl;
import io.sundr.deps.org.apache.velocity.runtime.resource.ResourceManagerImpl;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import io.sundr.deps.org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import io.sundr.deps.org.apache.velocity.util.introspection.UberspectImpl;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class BreakTest {
    @Test
    void renderRejectsNonScopeArgument() throws Exception {
        RuntimeInstance runtime = new RuntimeInstance();
        runtime.init(shadedRuntimeProperties());
        Break directive = new Break();
        Node childNode = new ValueNode("$notAScope", "plain text");
        Node rootNode = new ParentNode(childNode);
        directive.init(runtime, null, rootNode);

        assertThatThrownBy(() -> directive.render(null, new StringWriter(), rootNode))
                .isInstanceOf(VelocityException.class)
                .hasMessageContaining("$notAScope")
                .hasMessageContaining("io.sundr.deps.org.apache.velocity.runtime.directive.Scope");
    }

    @Test
    void evaluatedScopedBreakRejectsNonScopeReference() {
        VelocityEngine engine = new VelocityEngine(shadedRuntimeProperties());
        engine.init();
        VelocityContext context = new VelocityContext();
        context.put("notAScope", "plain text");

        assertThatThrownBy(() -> engine.evaluate(
                context,
                new StringWriter(),
                "scoped-break-test",
                "#break($notAScope)"))
                .isInstanceOf(VelocityException.class)
                .hasMessageContaining("$notAScope")
                .hasMessageContaining("io.sundr.deps.org.apache.velocity.runtime.directive.Scope");
    }

    private abstract static class BaseNode implements Node {
        @Override
        public void jjtOpen() {
        }

        @Override
        public void jjtClose() {
        }

        @Override
        public void jjtSetParent(Node node) {
        }

        @Override
        public Node jjtGetParent() {
            return null;
        }

        @Override
        public void jjtAddChild(Node node, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object jjtAccept(ParserVisitor visitor, Object data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object childrenAccept(ParserVisitor visitor, Object data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Token getFirstToken() {
            return null;
        }

        @Override
        public Token getLastToken() {
            return null;
        }

        @Override
        public int getType() {
            return 0;
        }

        @Override
        public Object init(InternalContextAdapter context, Object data) throws TemplateInitException {
            return data;
        }

        @Override
        public boolean evaluate(InternalContextAdapter context) throws MethodInvocationException {
            return false;
        }

        @Override
        public boolean render(InternalContextAdapter context, Writer writer)
                throws IOException, MethodInvocationException, ParseErrorException,
                ResourceNotFoundException {
            return false;
        }

        @Override
        public Object execute(Object object, InternalContextAdapter context)
                throws MethodInvocationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setInfo(int info) {
        }

        @Override
        public int getInfo() {
            return 0;
        }

        @Override
        public void setInvalid() {
        }

        @Override
        public boolean isInvalid() {
            return false;
        }

        @Override
        public int getLine() {
            return 1;
        }

        @Override
        public int getColumn() {
            return 1;
        }

        @Override
        public String getTemplateName() {
            return "break-test";
        }
    }

    private static final class ParentNode extends BaseNode {
        private final Node child;

        private ParentNode(Node child) {
            this.child = child;
        }

        @Override
        public Node jjtGetChild(int index) {
            if (index == 0) {
                return child;
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int jjtGetNumChildren() {
            return 1;
        }

        @Override
        public Object value(InternalContextAdapter context) throws MethodInvocationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String literal() {
            return "#break";
        }
    }

    private static final class ValueNode extends BaseNode {
        private final String literal;
        private final Object value;

        private ValueNode(String literal, Object value) {
            this.literal = literal;
            this.value = value;
        }

        @Override
        public Node jjtGetChild(int index) {
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int jjtGetNumChildren() {
            return 0;
        }

        @Override
        public Object value(InternalContextAdapter context) throws MethodInvocationException {
            return value;
        }

        @Override
        public String literal() {
            return literal;
        }
    }

    private static Properties shadedRuntimeProperties() {
        Properties properties = new Properties();
        properties.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                NullLogChute.class.getName());
        properties.setProperty(
                RuntimeConstants.RESOURCE_MANAGER_CLASS,
                ResourceManagerImpl.class.getName());
        properties.setProperty(
                RuntimeConstants.RESOURCE_MANAGER_CACHE_CLASS,
                ResourceCacheImpl.class.getName());
        properties.setProperty(RuntimeConstants.PARSER_POOL_CLASS, ParserPoolImpl.class.getName());
        properties.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, UberspectImpl.class.getName());
        properties.setProperty("file.resource.loader.class", FileResourceLoader.class.getName());
        properties.setProperty("string.resource.loader.class", StringResourceLoader.class.getName());
        return properties;
    }
}
