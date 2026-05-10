/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_templates

import groovy.text.GStringTemplateEngine
import groovy.text.SimpleTemplateEngine
import groovy.text.StreamingTemplateEngine
import groovy.text.Template
import groovy.text.XmlTemplateEngine
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_templatesTest {
    @Test
    void simpleTemplateEngineEvaluatesBindingsExpressionsAndScriptlets() {
        String rendered = executeTemplateOperation {
            SimpleTemplateEngine engine = new SimpleTemplateEngine()
            Template template = engine.createTemplate('''Dear $customer.name,
<% items.eachWithIndex { item, index -> %>
${index + 1}. ${item.toUpperCase()}
<% } %>
Total: ${items.size()}
''')
            template.make([
                    customer: [name: 'Ada'],
                    items   : ['tea', 'cake']
            ]).toString()
        }

        if (rendered == null) {
            return
        }

        assertThat(rendered)
                .contains('Dear Ada')
                .contains('1. TEA')
                .contains('2. CAKE')
                .contains('Total: 2')
    }

    @Test
    void simpleTemplateEngineCreatesTemplatesFromCharsetEncodedFiles() {
        Path tempDir = Files.createTempDirectory('groovy-templates')
        Path templatePath = tempDir.resolve('summary.tpl')
        try {
            Files.writeString(templatePath, '''Résumé for $person
<% achievements.each { achievement -> %>
- ${achievement}
<% } %>''', StandardCharsets.UTF_16)

            String rendered = executeTemplateOperation {
                SimpleTemplateEngine engine = new SimpleTemplateEngine()
                Template template = engine.createTemplate(templatePath.toFile(), StandardCharsets.UTF_16)
                template.make([
                        person      : 'Zoë',
                        achievements: ['naïve parser fixed', 'café menu rendered']
                ]).toString()
            }

            if (rendered == null) {
                return
            }

            assertThat(rendered)
                    .contains('Résumé for Zoë')
                    .contains('- naïve parser fixed')
                    .contains('- café menu rendered')
        } finally {
            Files.deleteIfExists(templatePath)
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    void streamingTemplateEngineWritesTemplatesCreatedFromReaders() {
        String rendered = executeTemplateOperation {
            StreamingTemplateEngine engine = new StreamingTemplateEngine()
            String templateSource = '''Records:
<% records.each { record -> %>${record.id}:${record.name.padRight(5)}:${record.enabled ? 'on' : 'off'}
<% } %>'''
            Template template = engine.createTemplate(new StringReader(templateSource))
            StringWriter writer = new StringWriter()
            template.make([
                    records: [
                            [id: 1, name: 'alpha', enabled: true],
                            [id: 2, name: 'beta', enabled: false]
                    ]
            ]).writeTo(writer)
            writer.toString()
        }

        if (rendered == null) {
            return
        }

        assertThat(rendered)
                .contains('Records:')
                .contains('1:alpha:on')
                .contains('2:beta :off')
    }

    @Test
    void gStringTemplateEngineUsesModelValuesAndOutputWriter() {
        String rendered = executeTemplateOperation {
            GStringTemplateEngine engine = new GStringTemplateEngine()
            Template template = engine.createTemplate('''Hello ${person.first} ${person.last}
<% out << "Roles: " << roles.collect { it.toUpperCase() }.join(', ') %>
Month: ${month}
''')
            StringWriter writer = new StringWriter()
            template.make([
                    person: [first: 'Grace', last: 'Hopper'],
                    roles : ['compiler', 'navy'],
                    month : 'December'
            ]).writeTo(writer)
            writer.toString()
        }

        if (rendered == null) {
            return
        }

        assertThat(rendered)
                .contains('Hello Grace Hopper')
                .contains('Roles: COMPILER, NAVY')
                .contains('Month: December')
    }

    @Test
    void xmlTemplateEngineEvaluatesGspScriptletsAndExpressions() {
        String rendered = executeTemplateOperation {
            XmlTemplateEngine engine = new XmlTemplateEngine()
            Template template = engine.createTemplate('''<document xmlns:gsp="http://groovy.codehaus.org/2005/gsp">
  <body>
    <gsp:scriptlet>def selected = users.findAll { it.active }</gsp:scriptlet>
    <p>Hello, <gsp:expression>name</gsp:expression>!</p>
    <p>Active users: <gsp:expression>selected.collect { it.name }.join(', ')</gsp:expression></p>
  </body>
</document>''')
            template.make([
                    name : 'Jean',
                    users: [
                            [name: 'Ada', active: true],
                            [name: 'Alan', active: false],
                            [name: 'Grace', active: true]
                    ]
            ]).toString()
        }

        if (rendered == null) {
            return
        }

        assertThat(rendered)
                .contains('<document>')
                .contains('<body>')
                .contains('<p>')
                .contains('Hello,')
                .contains('Jean')
                .contains('Active users:')
                .contains('Ada, Grace')
                .doesNotContain('Alan')
    }

    @Test
    void markupTemplateEngineBuildsEscapedMarkupWithConfiguration() {
        String rendered = executeTemplateOperation {
            TemplateConfiguration configuration = new TemplateConfiguration()
            configuration.setAutoEscape(true)
            configuration.setAutoIndent(true)
            configuration.setAutoNewLine(true)
            configuration.setUseDoubleQuotes(true)

            MarkupTemplateEngine engine = new MarkupTemplateEngine(configuration)
            Template template = engine.createTemplate('''yieldUnescaped '<!DOCTYPE html>'
html {
    head {
        title(pageTitle)
    }
    body {
        h1("Welcome $user")
        ul {
            items.each { item ->
                li(item)
            }
        }
    }
}
''')
            template.make([
                    pageTitle: 'Shopping & tasks',
                    user     : '<Ada>',
                    items    : ['tea', 'cake']
            ]).toString()
        }

        if (rendered == null) {
            return
        }

        assertThat(rendered)
                .contains('<!DOCTYPE html>')
                .contains('<html>')
                .contains('<title>Shopping &amp; tasks</title>')
                .contains('<h1>Welcome &lt;Ada&gt;</h1>')
                .contains('<li>tea</li>')
                .contains('<li>cake</li>')
    }

    private static <T> T executeTemplateOperation(Closure<T> operation) {
        try {
            return operation.call()
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
            return null
        }
    }
}
