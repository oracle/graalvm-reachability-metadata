/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_groovydoc

import org.codehaus.groovy.groovydoc.GroovyClassDoc
import org.codehaus.groovy.groovydoc.GroovyMethodDoc
import org.codehaus.groovy.groovydoc.GroovyPackageDoc
import org.codehaus.groovy.groovydoc.GroovyRootDoc
import org.codehaus.groovy.tools.groovydoc.GroovyDocTool
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_groovydocTest {
    @Test
    void groovyDocToolBuildsPackageAndClassDocsFromSource(@TempDir Path tempDir) {
        Path sourceRoot = tempDir.resolve('src')
        Path packageDir = sourceRoot.resolve('sample/docs')
        Files.createDirectories(packageDir)
        Files.writeString(packageDir.resolve('package.html'), '''
            <html>
            <body>
            <p>Package summary sentence. Additional package details.</p>
            </body>
            </html>
        '''.stripIndent())
        Files.writeString(packageDir.resolve('Greeter.groovy'), '''
            package sample.docs

            /**
             * Greets a configured recipient. Further class details.
             */
            class Greeter {
                private final String prefix

                /**
                 * Creates a greeter.
                 *
                 * @param prefix configured prefix
                 */
                Greeter(String prefix) {
                    this.prefix = prefix
                }

                /**
                 * Builds the greeting text.
                 *
                 * @param name recipient name
                 * @return complete greeting
                 */
                String greet(String name) {
                    "${prefix}, ${name}"
                }
            }
        '''.stripIndent())

        GroovyDocTool tool = new GroovyDocTool([sourceRoot.toString()] as String[])
        tool.add(['sample/docs/package.html', 'sample/docs/Greeter.groovy'])
        GroovyRootDoc rootDoc = tool.getRootDoc()

        GroovyPackageDoc packageDoc = rootDoc.packageNamed('sample/docs')
        assertThat(packageDoc).isNotNull()
        assertThat(packageDoc.nameWithDots()).isEqualTo('sample.docs')
        assertThat(packageDoc.summary()).contains('Package summary sentence.')
        assertThat(packageDoc.description()).contains('Additional package details.')
        assertThat(packageDoc.allClasses()*.name()).containsExactly('Greeter')

        GroovyClassDoc greeterDoc = rootDoc.classes().find { GroovyClassDoc classDoc -> classDoc.name() == 'Greeter' }
        assertThat(greeterDoc).isNotNull()
        assertThat(greeterDoc.qualifiedTypeName()).isEqualTo('sample.docs.Greeter')
        assertThat(greeterDoc.containingPackage().nameWithDots()).isEqualTo('sample.docs')
        assertThat(greeterDoc.commentText()).contains('Greets a configured recipient.')
        assertThat(greeterDoc.firstSentenceCommentText()).contains('Greets a configured recipient.')

        GroovyMethodDoc greetMethod = greeterDoc.methods().find { GroovyMethodDoc methodDoc -> methodDoc.name() == 'greet' }
        assertThat(greetMethod).isNotNull()
        assertThat(greetMethod.commentText()).contains('Builds the greeting text.')
        assertThat(greetMethod.returnType().typeName()).isEqualTo('java.lang.String')
        assertThat(greetMethod.parameters()*.name()).containsExactly('name')
        assertThat(greetMethod.parameters()*.typeName()).containsExactly('java.lang.String')
    }
}
