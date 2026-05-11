/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_plugin_parameter_documenter;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.usability.plugin.Expression;
import org.apache.maven.usability.plugin.ExpressionDocumentation;
import org.apache.maven.usability.plugin.io.xpp3.ParamdocXpp3Reader;
import org.apache.maven.usability.plugin.io.xpp3.ParamdocXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_plugin_parameter_documenterTest {
    @Test
    void expressionStoresScalarPropertiesAndLazilyCreatesPropertyCollections() {
        Expression expression = new Expression();

        assertThat(expression.isEditable()).isTrue();
        assertThat(expression.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(expression.getCliOptions()).isEmpty();
        assertThat(expression.getApiMethods()).isEmpty();

        expression.setSyntax("${project.version}");
        expression.setDescription("Resolved project version");
        expression.setConfiguration("<version>1.0.0</version>");
        expression.setDeprecation("Use ${project.artifact.selectedVersion}");
        expression.setBan("Use a fixed release version");
        expression.setEditable(false);
        expression.setModelEncoding("ISO-8859-1");
        expression.addCliOption("--version", "Display the Maven runtime version");
        expression.addApiMethod("MavenProject#getVersion()", "Read the version from the project model");

        assertThat(expression.getSyntax()).isEqualTo("${project.version}");
        assertThat(expression.getDescription()).isEqualTo("Resolved project version");
        assertThat(expression.getConfiguration()).isEqualTo("<version>1.0.0</version>");
        assertThat(expression.getDeprecation()).isEqualTo("Use ${project.artifact.selectedVersion}");
        assertThat(expression.getBan()).isEqualTo("Use a fixed release version");
        assertThat(expression.isEditable()).isFalse();
        assertThat(expression.getModelEncoding()).isEqualTo("ISO-8859-1");
        assertThat(expression.getCliOptions()).containsEntry("--version", "Display the Maven runtime version");
        assertThat(expression.getApiMethods()).containsEntry(
                "MavenProject#getVersion()",
                "Read the version from the project model");
    }

    @Test
    void expressionCanReplacePropertyCollections() {
        Expression expression = new Expression();
        Properties cliOptions = new Properties();
        cliOptions.setProperty("-DskipTests", "Skip test execution");
        Properties apiMethods = new Properties();
        apiMethods.setProperty("MavenProject#getProperties()", "Access user properties");

        expression.setCliOptions(cliOptions);
        expression.setApiMethods(apiMethods);

        assertThat(expression.getCliOptions()).isSameAs(cliOptions);
        assertThat(expression.getApiMethods()).isSameAs(apiMethods);
        assertThat(expression.getCliOptions()).containsEntry("-DskipTests", "Skip test execution");
        assertThat(expression.getApiMethods()).containsEntry("MavenProject#getProperties()", "Access user properties");
    }

    @Test
    void documentationMaintainsExpressionsAndSyntaxIndexCache() {
        ExpressionDocumentation documentation = new ExpressionDocumentation();
        Expression basedir = expression("${basedir}", "Base directory");
        Expression projectBuildDirectory = expression("${project.build.directory}", "Build directory");

        documentation.addExpression(basedir);
        documentation.addExpression(projectBuildDirectory);

        assertThat(documentation.getModelEncoding()).isEqualTo("UTF-8");
        assertThat(documentation.getExpressions()).containsExactly(basedir, projectBuildDirectory);
        @SuppressWarnings("unchecked")
        Map<String, Expression> firstIndex = documentation.getExpressionsBySyntax();
        assertThat(firstIndex).containsEntry("${basedir}", basedir)
                .containsEntry("${project.build.directory}", projectBuildDirectory);

        Expression projectVersion = expression("${project.version}", "Project version");
        documentation.addExpression(projectVersion);
        assertThat(documentation.getExpressionsBySyntax()).isSameAs(firstIndex);
        assertThat(firstIndex).doesNotContainKey("${project.version}");

        documentation.flushExpressionsBySyntax();
        assertThat(documentation.getExpressionsBySyntax()).containsEntry("${project.version}", projectVersion);

        documentation.removeExpression(basedir);
        documentation.setModelEncoding("UTF-16");
        assertThat(documentation.getExpressions()).containsExactly(projectBuildDirectory, projectVersion);
        assertThat(documentation.getModelEncoding()).isEqualTo("UTF-16");
    }

    @Test
    void documentationCanUseCallerProvidedExpressionList() {
        Expression first = expression("${settings.localRepository}", "Local repository");
        Expression second = expression("${project.groupId}", "Project groupId");
        List<Expression> expressions = new ArrayList<>();
        expressions.add(first);
        expressions.add(second);
        ExpressionDocumentation documentation = new ExpressionDocumentation();

        documentation.setExpressions(expressions);

        assertThat(documentation.getExpressions()).isSameAs(expressions);
        assertThat(documentation.getExpressionsBySyntax()).containsEntry("${settings.localRepository}", first)
                .containsEntry("${project.groupId}", second);
    }

    @Test
    void writerSerializesOnlyNonDefaultExpressionFields() throws Exception {
        Expression editableExpression = expression("${basedir}", "Base directory");
        editableExpression.addCliOption("-f", "Select an alternate project file");
        editableExpression.addApiMethod("MavenProject#getBasedir()", "Get the project base directory");
        Expression readOnlyExpression = expression("${project.build.outputDirectory}", "Compiled classes directory");
        readOnlyExpression.setEditable(false);
        ExpressionDocumentation documentation = new ExpressionDocumentation();
        documentation.addExpression(editableExpression);
        documentation.addExpression(readOnlyExpression);

        StringWriter output = new StringWriter();
        new ParamdocXpp3Writer().write(output, documentation);

        String xml = output.toString();
        assertThat(xml).contains("<paramdoc>")
                .contains("<expressions>")
                .contains("<syntax>${basedir}</syntax>")
                .contains("<description>Base directory</description>")
                .contains("<cliOptions>")
                .contains("<key>-f</key>")
                .contains("<value>Select an alternate project file</value>")
                .contains("<apiMethods>")
                .contains("<key>MavenProject#getBasedir()</key>")
                .contains("<editable>false</editable>")
                .doesNotContain("<editable>true</editable>");
    }

    @Test
    void writerUsesDocumentationModelEncodingInXmlDeclaration() throws Exception {
        ExpressionDocumentation documentation = new ExpressionDocumentation();
        documentation.setModelEncoding("UTF-16");
        documentation.addExpression(expression("${project.url}", "Project website URL"));

        StringWriter output = new StringWriter();
        new ParamdocXpp3Writer().write(output, documentation);

        assertThat(output.toString())
                .startsWith("<?xml version=\"1.0\" encoding=\"UTF-16\"?>")
                .contains("<syntax>${project.url}</syntax>")
                .contains("<description>Project website URL</description>");
    }

    @Test
    void readerParsesCompleteStrictDocumentAndTrimsTextValues() throws Exception {
        String xml = """
                <paramdoc>
                  <expressions>
                    <expression>
                      <syntax> ${project.version} </syntax>
                      <description> Project version </description>
                      <configuration> &lt;version&gt;1.0.0&lt;/version&gt; </configuration>
                      <cliOptions>
                        <cliOption>
                          <key>-Drevision</key>
                          <value>  Set the revision property  </value>
                        </cliOption>
                      </cliOptions>
                      <apiMethods>
                        <apiMethod>
                          <key>MavenProject#setVersion(String)</key>
                          <value>  Change the version programmatically  </value>
                        </apiMethod>
                      </apiMethods>
                      <deprecation> Use ${project.artifact.selectedVersion} </deprecation>
                      <ban> Use release versions </ban>
                      <editable>false</editable>
                    </expression>
                  </expressions>
                </paramdoc>
                """;

        ExpressionDocumentation documentation = new ParamdocXpp3Reader().read(new StringReader(xml));

        assertThat(documentation.getExpressions()).hasSize(1);
        Expression expression = (Expression) documentation.getExpressions().get(0);
        assertThat(expression.getSyntax()).isEqualTo("${project.version}");
        assertThat(expression.getDescription()).isEqualTo("Project version");
        assertThat(expression.getConfiguration()).isEqualTo("<version>1.0.0</version>");
        assertThat(expression.getCliOptions()).containsEntry("-Drevision", "Set the revision property");
        assertThat(expression.getApiMethods()).containsEntry(
                "MavenProject#setVersion(String)",
                "Change the version programmatically");
        assertThat(expression.getDeprecation()).isEqualTo("Use ${project.artifact.selectedVersion}");
        assertThat(expression.getBan()).isEqualTo("Use release versions");
        assertThat(expression.isEditable()).isFalse();
        assertThat(documentation.getExpressionsBySyntax()).containsEntry("${project.version}", expression);
    }

    @Test
    void writerOutputCanBeReadBackWithEscapedCharactersAndMultipleExpressions() throws Exception {
        Expression first = expression("${project.name}", "Project <display> & human-readable name");
        first.setConfiguration("<name>Example & Demo</name>");
        first.addCliOption("-N", "Do not recurse into sub-projects");
        Expression second = expression("${session.executionRootDirectory}", "Execution root directory");
        second.setEditable(false);
        second.setDeprecation("Prefer ${maven.multiModuleProjectDirectory}");
        second.setBan("Do not mutate the session root");
        second.addApiMethod("MavenSession#getExecutionRootDirectory()", "Read root directory");
        ExpressionDocumentation original = new ExpressionDocumentation();
        original.addExpression(first);
        original.addExpression(second);

        StringWriter output = new StringWriter();
        new ParamdocXpp3Writer().write(output, original);
        ExpressionDocumentation parsed = new ParamdocXpp3Reader().read(new StringReader(output.toString()));

        assertThat(parsed.getExpressions()).hasSize(2);
        Expression parsedFirst = (Expression) parsed.getExpressions().get(0);
        Expression parsedSecond = (Expression) parsed.getExpressions().get(1);
        assertThat(parsedFirst.getSyntax()).isEqualTo("${project.name}");
        assertThat(parsedFirst.getDescription()).isEqualTo("Project <display> & human-readable name");
        assertThat(parsedFirst.getConfiguration()).isEqualTo("<name>Example & Demo</name>");
        assertThat(parsedFirst.getCliOptions()).containsEntry("-N", "Do not recurse into sub-projects");
        assertThat(parsedFirst.isEditable()).isTrue();
        assertThat(parsedSecond.getSyntax()).isEqualTo("${session.executionRootDirectory}");
        assertThat(parsedSecond.getDeprecation()).isEqualTo("Prefer ${maven.multiModuleProjectDirectory}");
        assertThat(parsedSecond.getBan()).isEqualTo("Do not mutate the session root");
        assertThat(parsedSecond.getApiMethods()).containsEntry(
                "MavenSession#getExecutionRootDirectory()",
                "Read root directory");
        assertThat(parsedSecond.isEditable()).isFalse();
    }

    @Test
    void strictReaderRejectsDuplicateAndUnknownTags() {
        ParamdocXpp3Reader reader = new ParamdocXpp3Reader();
        String duplicateSyntax = """
                <paramdoc>
                  <expressions>
                    <expression>
                      <syntax>${project.version}</syntax>
                      <syntax>${project.groupId}</syntax>
                    </expression>
                  </expressions>
                </paramdoc>
                """;
        String unknownTag = """
                <paramdoc>
                  <expressions>
                    <expression>
                      <syntax>${project.version}</syntax>
                      <unexpected>ignored only in non-strict mode</unexpected>
                    </expression>
                  </expressions>
                </paramdoc>
                """;

        assertThatExceptionOfType(XmlPullParserException.class)
                .isThrownBy(() -> reader.read(new StringReader(duplicateSyntax)))
                .withMessageContaining("Duplicated tag: 'syntax'");
        assertThatExceptionOfType(XmlPullParserException.class)
                .isThrownBy(() -> reader.read(new StringReader(unknownTag)))
                .withMessageContaining("Unrecognised tag: 'unexpected'");
    }

    @Test
    void nonStrictReaderIgnoresUnknownTagsButStillParsesKnownContent() throws Exception {
        String xml = """
                <paramdoc>
                  <expressions>
                    <expression>
                      <syntax>${project.artifactId}</syntax>
                      <description>Artifact identifier</description>
                      <unknown />
                    </expression>
                  </expressions>
                  <ignoredRootChild>ignored in non-strict mode</ignoredRootChild>
                </paramdoc>
                """;

        ExpressionDocumentation documentation = new ParamdocXpp3Reader().read(new StringReader(xml), false);

        assertThat(documentation.getExpressions()).hasSize(1);
        Expression expression = (Expression) documentation.getExpressions().get(0);
        assertThat(expression.getSyntax()).isEqualTo("${project.artifactId}");
        assertThat(expression.getDescription()).isEqualTo("Artifact identifier");
    }

    @Test
    void readerCanResolveDefaultXhtmlEntities() throws Exception {
        String xml = """
                <paramdoc>
                  <expressions>
                    <expression>
                      <syntax>${project.description}</syntax>
                      <description>Copyright &copy; Maven &mdash; build tool</description>
                    </expression>
                  </expressions>
                </paramdoc>
                """;

        ParamdocXpp3Reader reader = new ParamdocXpp3Reader();
        ExpressionDocumentation documentation = reader.read(new StringReader(xml));

        assertThat(reader.getAddDefaultEntities()).isTrue();
        Expression expression = (Expression) documentation.getExpressions().get(0);
        assertThat(expression.getDescription()).isEqualTo("Copyright \u00a9 Maven \u2014 build tool");
    }

    @Test
    void readerCanDisableDefaultXhtmlEntityResolution() {
        ParamdocXpp3Reader reader = new ParamdocXpp3Reader();
        reader.setAddDefaultEntities(false);
        String xml = """
                <paramdoc>
                  <expressions>
                    <expression>
                      <syntax>${project.description}</syntax>
                      <description>Copyright &copy; Maven</description>
                    </expression>
                  </expressions>
                </paramdoc>
                """;

        assertThat(reader.getAddDefaultEntities()).isFalse();
        assertThatExceptionOfType(XmlPullParserException.class)
                .isThrownBy(() -> reader.read(new StringReader(xml)))
                .withMessageContaining("could not resolve entity named 'copy'");
    }

    @Test
    void readerHelperMethodsConvertPrimitiveValuesAndApplyStrictValidation() throws Exception {
        ParamdocXpp3Reader reader = new ParamdocXpp3Reader();

        assertThat(reader.getBooleanValue("true", "editable", null)).isTrue();
        assertThat(reader.getBooleanValue(null, "editable", null)).isFalse();
        assertThat(reader.getCharacterValue("abc", "letter", null)).isEqualTo('a');
        assertThat(reader.getCharacterValue(null, "letter", null)).isEqualTo((char) 0);
        assertThat(reader.getDateValue(null, "created", null)).isNull();
        assertThat(reader.getIntegerValue("42", "count", null, true)).isEqualTo(42);
        assertThat(reader.getLongValue("123456789", "count", null, true)).isEqualTo(123456789L);
        assertThat(reader.getShortValue("7", "count", null, true)).isEqualTo((short) 7);
        assertThat(reader.getFloatValue("1.5", "ratio", null, true)).isEqualTo(1.5F);
        assertThat(reader.getDoubleValue("2.25", "ratio", null, true)).isEqualTo(2.25D);
        assertThat(reader.getIntegerValue("not-a-number", "count", null, false)).isZero();
        assertThat(reader.getRequiredAttributeValue("present", "name", null, true)).isEqualTo("present");
        assertThat(reader.getRequiredAttributeValue(null, "optional", null, false)).isNull();
        assertThat(reader.getTrimmedValue("  text  ")).isEqualTo("text");
        assertThat(reader.getTrimmedValue(null)).isNull();

        assertThatThrownBy(() -> reader.getIntegerValue("not-a-number", "count", null, true))
                .isInstanceOf(XmlPullParserException.class)
                .hasMessageContaining("must be an integer");
        assertThatThrownBy(() -> reader.getRequiredAttributeValue(null, "required", null, true))
                .isInstanceOf(XmlPullParserException.class)
                .hasMessageContaining("Missing required value for attribute 'required'");
    }

    private static Expression expression(String syntax, String description) {
        Expression expression = new Expression();
        expression.setSyntax(syntax);
        expression.setDescription(description);
        return expression;
    }
}
