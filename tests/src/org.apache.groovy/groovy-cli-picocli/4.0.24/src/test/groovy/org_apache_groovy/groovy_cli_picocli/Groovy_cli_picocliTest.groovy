/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_cli_picocli

import groovy.cli.Option
import groovy.cli.TypedOption
import groovy.cli.Unparsed
import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_cli_picocliTest {
    @TempDir
    Path temporaryDirectory

    @Test
    void parsesDynamicDslOptionsWithTypesConvertersMapsAndRemainingArguments() {
        CliBuilder cli = new CliBuilder(name: 'copy', stopAtNonOption: false)
        cli.v(longOpt: 'verbose', 'enable verbose logging')
        cli._(longOpt: 'dry-run', 'show what would be copied')
        TypedOption<File> output = cli.o(longOpt: 'output', type: File, argName: 'file', 'write output to file')
        TypedOption<Integer> count = cli.c(longOpt: 'count', type: Integer, defaultValue: '7', 'number of copies')
        cli.D(type: Map, argName: 'property=value', 'set a named property')
        cli.I(longOpt: 'include', args: '+', valueSeparator: ',', argName: 'path', 'include paths')
        cli.L(longOpt: 'label', convert: { String value -> value.reverse() }, 'converted label')

        OptionAccessor options = cli.parse([
                '-v',
                '--dry-run',
                '--output', 'build/out.txt',
                '-Dmode=fast',
                '-I', 'src,resources',
                '-I', 'generated',
                '--label', 'abc',
                'input.txt',
                '--count', '9'
        ] as String[])

        assertThat(options).isNotNull()
        assertThat(options.hasOption('verbose')).isTrue()
        assertThat(options.verbose).isTrue()
        assertThat(options.'dry-run').isTrue()
        assertThat(options.getOptionValue(output)).isEqualTo(new File('build/out.txt'))
        assertThat(options.getAt(count)).isEqualTo(9)
        assertThat(options.D as Map<String, String>).containsEntry('mode', 'fast')
        assertThat(options.I).isEqualTo('src')
        assertThat(options.Is as List<String>).containsExactly('src', 'resources', 'generated')
        assertThat(options.label).isEqualTo('cba')
        assertThat(options.arguments()).containsExactly('input.txt')
    }

    @Test
    void supportsSingleHyphenLongOptionsAndAttachedOptionValues() {
        CliBuilder cli = new CliBuilder(name: 'runner', acceptLongOptionsWithSingleHyphen: true)
        cli._(longOpt: 'colour', type: String, argName: 'name', 'selected colour')
        cli.t(longOpt: 'timeout', type: Integer, argName: 'seconds', 'timeout in seconds')

        OptionAccessor options = cli.parse(['-colour', 'blue', '-t15'] as String[])

        assertThat(options).isNotNull()
        assertThat(options.hasOption('colour')).isTrue()
        assertThat(options.colour).isEqualTo('blue')
        assertThat(options.timeout).isEqualTo(15)
    }

    @Test
    void expandsArgumentFilesAndCanTreatThemAsLiteralArguments() {
        Path argumentFile = temporaryDirectory.resolve('command.args')
        Files.writeString(argumentFile, '--name\nAda\nfirst\nsecond\n')

        String argumentFileReference = "@$argumentFile"
        CliBuilder expandingCli = new CliBuilder(name: 'greeter')
        expandingCli._(longOpt: 'name', type: String, 'name to greet')
        OptionAccessor expandedOptions = expandingCli.parse([argumentFileReference] as String[])

        assertThat(expandedOptions).isNotNull()
        assertThat(expandedOptions.name).isEqualTo('Ada')
        assertThat(expandedOptions.arguments()).containsExactly('first', 'second')

        CliBuilder literalCli = new CliBuilder(name: 'greeter', expandArgumentFiles: false)
        literalCli._(longOpt: 'name', type: String, 'name to greet')
        OptionAccessor literalOptions = literalCli.parse([argumentFileReference] as String[])

        assertThat(literalOptions).isNotNull()
        assertThat(literalOptions.hasOption('name')).isFalse()
        assertThat(literalOptions.arguments()).containsExactly(argumentFileReference)
    }

    @Test
    void capturesUsageAndValidationErrorsWithConfiguredWriters() {
        StringWriter usageBuffer = new StringWriter()
        CliBuilder usageCli = new CliBuilder(
                name: 'secure-copy',
                usage: 'secure-copy [options] source target',
                header: 'Copies files securely.',
                footer: 'Use responsibly.',
                width: 60
        )
        usageCli.writer = new PrintWriter(usageBuffer)
        usageCli.h(longOpt: 'help', 'display help')

        usageCli.usage()

        assertThat(usageBuffer.toString())
                .contains('secure-copy [options] source target')
                .contains('Copies files securely.')
                .contains('-h, --help')
                .contains('Use responsibly.')

        StringWriter errorBuffer = new StringWriter()
        CliBuilder validationCli = new CliBuilder(name: 'secure-copy')
        validationCli.errorWriter = new PrintWriter(errorBuffer)
        validationCli.u(longOpt: 'user', type: String, required: true, 'remote user')

        OptionAccessor options = validationCli.parse([] as String[])

        assertThat(options).isNull()
        assertThat(errorBuffer.toString())
                .contains('error:')
                .contains('Missing required option')
                .contains('--user')
                .contains('Usage:')
    }

    @Test
    void parsesAnnotatedInterfacesIntoTypedOptionViews() {
        CliBuilder cli = new CliBuilder(usage: 'ls [options] [files]')

        ListingOptions options = cli.parseFromSpec(ListingOptions, ['-al', '--sort', 'time', '*.groovy'] as String[])

        assertThat(options.all()).isTrue()
        assertThat(options.longFormat()).isTrue()
        assertThat(options.sort()).isEqualTo('time')
        assertThat(options.remaining()).containsExactly('*.groovy')
    }

    @Test
    void parsesAnnotatedInstancesIntoFieldsGettersAndSetters() {
        UploadOptions options = new UploadOptions()
        CliBuilder cli = new CliBuilder(usage: 'upload [options] files')

        UploadOptions returnedOptions = cli.parseFromInstance(options, [
                '--name', 'release',
                '--retry-count', '5',
                '--tag', 'stable,linux',
                '-v',
                'artifact.zip',
                'checksum.txt'
        ] as String[])

        assertThat(returnedOptions).isSameAs(options)
        assertThat(options.name).isEqualTo('release')
        assertThat(options.retryCount).isEqualTo(5)
        assertThat(options.tags).containsExactly('stable', 'linux')
        assertThat(options.verbose).isTrue()
        assertThat(options.files).containsExactly('artifact.zip', 'checksum.txt')
    }

    @Test
    void appliesDefaultsAndBooleanInitializationForAnnotatedInstances() {
        UploadOptions options = new UploadOptions()
        CliBuilder cli = new CliBuilder(usage: 'upload [options] files')

        cli.parseFromInstance(options, ['artifact.zip'] as String[])

        assertThat(options.name).isEqualTo('anonymous')
        assertThat(options.retryCount).isEqualTo(2)
        assertThat(options.tags).isEmpty()
        assertThat(options.verbose).isFalse()
        assertThat(options.files).containsExactly('artifact.zip')
    }

    static interface ListingOptions {
        @Option(shortName = 'a', description = 'display all files')
        boolean all()

        @Option(shortName = 'l', description = 'use a long listing format')
        boolean longFormat()

        @Option(longName = 'sort', description = 'sort order', defaultValue = 'name')
        String sort()

        @Unparsed(description = 'files to list')
        List<String> remaining()
    }

    static class UploadOptions {
        @Option(longName = 'name', description = 'upload name', defaultValue = 'anonymous')
        String name

        @Option(longName = 'retry-count', description = 'retry count', defaultValue = '2')
        int retryCount

        @Option(shortName = 't', longName = 'tag', description = 'upload tags', numberOfArgumentsString = '+', valueSeparator = ',')
        List<String> tags = []

        boolean verbose

        @Unparsed(description = 'files to upload')
        List<String> files = []

        @Option(shortName = 'v', description = 'verbose output')
        void setVerbose(boolean verbose) {
            this.verbose = verbose
        }
    }
}
