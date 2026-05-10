/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_docgenerator

import org.apache.groovy.docgenerator.DocGenerator
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_docgeneratorTest {
    @TempDir
    Path temporaryDirectory

    @Test
    void parsesSourceIntoDocumentedGdkTargetTypesAndMethods() {
        Path sourceFile = writeSampleSource('SampleGroovyMethods.java')
        DocGenerator generator = new DocGenerator([sourceFile.toFile()], temporaryDirectory.resolve('docs').toFile())

        def docTypes = generator.docSource.allDocTypes
        def stringDocType = docTypes.find { it.fullyQualifiedClassName == 'java.lang.String' }
        def intDocType = docTypes.find { it.fullyQualifiedClassName == 'primitives-and-primitive-arrays.int' }

        assertThat(generator.sourceFiles).containsExactly(sourceFile.toFile())
        assertThat(generator.outputDir).isEqualTo(temporaryDirectory.resolve('docs').toFile())
        assertThat(generator.docSource.packages*.name)
                .contains('java.lang', 'primitives-and-primitive-arrays')
        assertThat(docTypes*.fullyQualifiedClassName)
                .contains('java.lang.String', 'primitives-and-primitive-arrays.int')
        assertThat(stringDocType.simpleClassName).isEqualTo('String')
        assertThat(stringDocType.packageName).isEqualTo('java.lang')
        assertThat(stringDocType.interface).isFalse()
        assertThat(stringDocType.docMethods*.name).containsExactly('decorate')
        assertThat(intDocType.docMethods*.name).containsExactly('squared')

        def decorateMethod = stringDocType.docMethods.first()
        assertThat(decorateMethod.name).isEqualTo('decorate')
        assertThat(decorateMethod.parameters*.name).containsExactly('suffix')
        assertThat(decorateMethod.parametersSignature).isEqualTo('java.lang.String')
        assertThat(decorateMethod.returnComment).contains('decorated text')
        assertThat(decorateMethod.parameterComments).containsEntry('suffix', ' suffix appended to the receiver')
        assertThat(decorateMethod.sinceComment).isEqualTo('1.0')
        assertThat(decorateMethod.sortKey).contains('decorate java.lang.String java.lang.String')
    }

    @Test
    void generatesLinkedHtmlDocumentationFromCommandLine() {
        Path sourceFile = writeRenderableSource('LinkedGroovyMethods.java')
        Path outputDirectory = temporaryDirectory.resolve('html-docs')

        boolean generated = runDocGeneratorAllowingNativeTemplateFailure([
                '--outputDir', outputDirectory.toString(),
                '--title', 'Sample GDK',
                sourceFile.toString()
        ])
        if (generated) {
            assertThat(outputDirectory.resolve('index.html')).exists()
            assertThat(outputDirectory.resolve('overview-summary.html')).exists()
            assertThat(outputDirectory.resolve('allclasses-frame.html')).exists()
            assertThat(outputDirectory.resolve('stylesheet.css')).exists()
            assertThat(outputDirectory.resolve('groovy.ico')).exists()
            assertThat(read(outputDirectory.resolve('package-list')))
                    .contains('java.lang')
                    .contains('primitives-and-primitive-arrays')

            String stringPage = read(outputDirectory.resolve('java/lang/String.html'))
            assertThat(stringPage)
                    .contains('<title>String (Sample GDK)</title>')
                    .contains('<h2>Class String</h2>')
                    .contains('<a href="#decorate()">decorate</a>')
                    .contains('Decorates <code>self</code> for generated documentation.')
                    .contains('<dd>1.0</dd>')

            String primitivePage = read(outputDirectory.resolve('primitives-and-primitive-arrays/int.html'))
            assertThat(primitivePage)
                    .contains('<title>int (Sample GDK)</title>')
                    .contains('<a href="#squared()">squared</a>')
                    .contains('Squares a primitive receiver.')

            String indexPage = read(outputDirectory.resolve('index-all.html'))
            assertThat(indexPage)
                    .contains('<a HREF="#_D_">D</a>')
                    .contains('<a href="java/lang/String.html#decorate()"><b>decorate()</b></a>')
                    .contains('<a href="primitives-and-primitive-arrays/int.html#squared()"><b>squared()</b></a>')
        }
    }

    @Test
    void commandLineReportsVersion() {
        PrintStream originalOut = System.out
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream()
        try {
            System.out = new PrintStream(capturedOut, true, StandardCharsets.UTF_8.name())
            DocGenerator.main('--version')
        } finally {
            System.out = originalOut
        }

        assertThat(capturedOut.toString(StandardCharsets.UTF_8.name()))
                .contains(GroovySystem.version)
    }

    @Test
    void collectsInheritedGdkMethodsFromDocumentedSuperTypes() {
        List<Path> sourceFiles = writeInheritedMethodSources()
        DocGenerator generator = new DocGenerator(sourceFiles*.toFile(), temporaryDirectory.resolve('inherited-docs').toFile())

        def docTypes = generator.docSource.allDocTypes
        def parentDocType = docTypes.find { it.fullyQualifiedClassName == 'target.ParentTarget' }
        def childDocType = docTypes.find { it.fullyQualifiedClassName == 'target.ChildTarget' }
        def inheritedEntry = childDocType.inheritedMethods.find { inheritedType, inheritedMethods ->
            inheritedType.fullyQualifiedClassName == 'target.ParentTarget'
        }

        assertThat(docTypes*.fullyQualifiedClassName)
                .contains('target.ParentTarget', 'target.ChildTarget')
        assertThat(parentDocType.docMethods*.name).containsExactly('sharedBehavior')
        assertThat(childDocType.docMethods*.name).containsExactly('childBehavior')
        assertThat(parentDocType.inheritedMethods).isEmpty()
        assertThat(inheritedEntry).isNotNull()
        assertThat(inheritedEntry.value*.name).containsExactly('sharedBehavior')
        assertThat(inheritedEntry.value.first().declaringDocType.fullyQualifiedClassName)
                .isEqualTo('target.ParentTarget')
    }

    private Path writeSampleSource(String fileName) {
        Path sourceFile = temporaryDirectory.resolve(fileName)
        Files.writeString(sourceFile, '''
            package sample.gdk;

            /** Public static methods in this class mimic GDK extension methods. */
            public final class SampleGroovyMethods {
                /**
                 * Decorates {@code self} and references {@link java.util.List}.
                 * The second sentence verifies first-sentence handling in generated indexes.
                 *
                 * @param self the receiver
                 * @param suffix suffix appended to the receiver
                 * @return decorated text
                 * @since 1.0
                 * @see java.lang.String#trim()
                 */
                public static java.lang.String decorate(java.lang.String self, java.lang.String suffix) {
                    return self + suffix;
                }

                /**
                 * Squares a primitive receiver.
                 *
                 * @param self the receiver
                 * @return the square
                 */
                public static int squared(int self) {
                    return self * self;
                }

                /** Should be ignored because it is deprecated. */
                @Deprecated
                public static java.lang.String old(java.lang.String self) {
                    return self;
                }

                /** Should be ignored because it is not static. */
                public java.lang.String instance(java.lang.String self) {
                    return self;
                }

                /** Should be ignored because it is not public. */
                private static java.lang.String hidden(java.lang.String self) {
                    return self;
                }
            }
            '''.stripIndent())
        return sourceFile
    }

    private Path writeRenderableSource(String fileName) {
        Path sourceFile = temporaryDirectory.resolve(fileName)
        Files.writeString(sourceFile, '''
            package sample.gdk;

            /** Public static methods in this class mimic GDK extension methods. */
            public final class RenderableGroovyMethods {
                /**
                 * Decorates {@code self} for generated documentation.
                 * The second sentence verifies first-sentence handling in generated indexes.
                 *
                 * @param self the receiver
                 * @since 1.0
                 */
                public static void decorate(java.lang.String self) {
                }

                /**
                 * Squares a primitive receiver.
                 *
                 * @param self the receiver
                 * @return the square
                 */
                public static int squared(int self) {
                    return self * self;
                }
            }
            '''.stripIndent())
        return sourceFile
    }

    private List<Path> writeInheritedMethodSources() {
        Path targetDirectory = temporaryDirectory.resolve('target')
        Path methodDirectory = temporaryDirectory.resolve('sample/gdk')
        Files.createDirectories(targetDirectory)
        Files.createDirectories(methodDirectory)

        Path parentFile = targetDirectory.resolve('ParentTarget.java')
        Files.writeString(parentFile, '''
            package target;

            /** Target type that owns a documented GDK method. */
            public class ParentTarget {
            }
            '''.stripIndent())

        Path childFile = targetDirectory.resolve('ChildTarget.java')
        Files.writeString(childFile, '''
            package target;

            /** Target type that should inherit documented GDK methods from ParentTarget. */
            public class ChildTarget extends ParentTarget {
            }
            '''.stripIndent())

        Path methodFile = methodDirectory.resolve('InheritedGroovyMethods.java')
        Files.writeString(methodFile, '''
            package sample.gdk;

            /** Public static methods in this class mimic GDK extension methods. */
            public final class InheritedGroovyMethods {
                /**
                 * Adds behavior documented directly on the parent target type.
                 *
                 * @param self the receiver
                 * @return the shared behavior name
                 */
                public static java.lang.String sharedBehavior(target.ParentTarget self) {
                    return "shared";
                }

                /**
                 * Adds behavior documented directly on the child target type.
                 *
                 * @param self the receiver
                 * @return the child behavior name
                 */
                public static java.lang.String childBehavior(target.ChildTarget self) {
                    return "child";
                }
            }
            '''.stripIndent())

        return [parentFile, childFile, methodFile]
    }

    private static boolean runDocGeneratorAllowingNativeTemplateFailure(List<String> arguments) {
        try {
            DocGenerator.main(arguments as String[])
            return true
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
            return false
        }
    }

    private static String read(Path file) {
        return Files.readString(file)
    }
}
