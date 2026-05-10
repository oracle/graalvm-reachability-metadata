/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_groovydoc

import org.codehaus.groovy.groovydoc.GroovyClassDoc
import org.codehaus.groovy.groovydoc.GroovyConstructorDoc
import org.codehaus.groovy.groovydoc.GroovyFieldDoc
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

    @Test
    void groovyDocToolBuildsJavaClassDocsWithMembers(@TempDir Path tempDir) {
        Path sourceRoot = tempDir.resolve('src')
        Path packageDir = sourceRoot.resolve('sample/docs')
        Files.createDirectories(packageDir)
        Files.writeString(packageDir.resolve('JavaStore.java'), '''
            package sample.docs;

            /**
             * Stores documented names from Java sources.
             */
            public class JavaStore {
                /** Default capacity for new stores. */
                public static final int DEFAULT_CAPACITY = 4;

                /**
                 * Creates an empty store.
                 */
                public JavaStore() {
                }

                /**
                 * Adds a name to the store.
                 *
                 * @param name name to store
                 * @return number of stored names
                 */
                public int addName(String name) {
                    return 1;
                }

            }
        '''.stripIndent())

        GroovyDocTool tool = new GroovyDocTool([sourceRoot.toString()] as String[])
        tool.add(['sample/docs/JavaStore.java'])
        GroovyRootDoc rootDoc = tool.getRootDoc()

        GroovyPackageDoc packageDoc = rootDoc.packageNamed('sample/docs')
        assertThat(packageDoc).isNotNull()
        assertThat(packageDoc.allClasses()*.name()).containsExactly('JavaStore')

        GroovyClassDoc javaStoreDoc = rootDoc.classes().find { GroovyClassDoc classDoc -> classDoc.name() == 'JavaStore' }
        assertThat(javaStoreDoc).isNotNull()
        assertThat(javaStoreDoc.qualifiedTypeName()).isEqualTo('sample.docs.JavaStore')
        assertThat(javaStoreDoc.commentText()).contains('Stores documented names from Java sources.')

        GroovyFieldDoc capacityField = javaStoreDoc.fields().find { GroovyFieldDoc fieldDoc ->
            fieldDoc.name() == 'DEFAULT_CAPACITY'
        }
        assertThat(capacityField).isNotNull()
        assertThat(capacityField.isStatic()).isTrue()
        assertThat(capacityField.isFinal()).isTrue()
        assertThat(capacityField.type().typeName()).isEqualTo('int')
        assertThat(capacityField.commentText()).contains('Default capacity for new stores.')

        List<GroovyConstructorDoc> constructors = javaStoreDoc.constructors() as List<GroovyConstructorDoc>
        assertThat(constructors).hasSize(1)
        GroovyConstructorDoc constructorDoc = constructors.first()
        assertThat(constructorDoc.commentText()).contains('Creates an empty store.')
        assertThat(constructorDoc.parameters()).isEmpty()

        GroovyMethodDoc addNameMethod = javaStoreDoc.methods().find { GroovyMethodDoc methodDoc ->
            methodDoc.name() == 'addName'
        }
        assertThat(addNameMethod).isNotNull()
        assertThat(addNameMethod.returnType().typeName()).isEqualTo('int')
        assertThat(addNameMethod.commentText()).contains('Adds a name to the store.')
        assertThat(addNameMethod.parameters()*.name()).containsExactly('name')
        assertThat(addNameMethod.parameters()*.typeName()).containsExactly('java.lang.String')
    }
}
