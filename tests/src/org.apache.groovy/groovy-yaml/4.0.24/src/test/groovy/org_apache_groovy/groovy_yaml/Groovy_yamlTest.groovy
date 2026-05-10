/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_yaml

import groovy.json.JsonSlurper
import groovy.yaml.YamlBuilder
import groovy.yaml.YamlSlurper
import org.apache.groovy.yaml.util.YamlConverter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

public class Groovy_yamlTest {
    @TempDir
    Path temporaryDirectory

    @Test
    void parsesYamlScalarsCollectionsAndBlockText() {
        String yaml = '''\
---
name: Ada Lovelace
active: true
score: 42
ratio: 3.5
missing: null
job:
  config:
    retries: 3
    tags:
      - compiler
      - yaml
  command: |
    echo first
    echo second
matrix:
  - os: linux
    java: 21
  - os: macos
    java: 17
'''

        Map parsed = new YamlSlurper().parseText(yaml) as Map

        assertThat(parsed.name).isEqualTo('Ada Lovelace')
        assertThat(parsed.active).isEqualTo(true)
        assertThat(parsed.score).isEqualTo(42)
        assertThat(parsed.ratio).isEqualTo(3.5)
        assertThat(parsed.missing).isNull()
        Map job = parsed['job'] as Map
        Map config = job['config'] as Map
        assertThat(config['retries']).isEqualTo(3)
        assertThat(config['tags']).containsExactly('compiler', 'yaml')
        assertThat(job['command']).isEqualTo('echo first\necho second\n')
        assertThat(parsed['matrix']).hasSize(2)
        assertThat(parsed['matrix'][0]).containsEntry('os', 'linux')
        assertThat(parsed['matrix'][1]).containsEntry('java', 17)
    }

    @Test
    void parsesYamlFromReaderInputStreamFileAndPath() {
        String yaml = '''\
---
service:
  name: inventory
  ports: [8080, 8081]
  labels:
    tier: backend
'''
        YamlSlurper slurper = new YamlSlurper()

        Map fromReader = slurper.parse(new StringReader(yaml)) as Map
        Map fromStream = slurper.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) as Map

        Path yamlPath = temporaryDirectory.resolve('service.yaml')
        Files.writeString(yamlPath, yaml, StandardCharsets.UTF_8)
        Map fromPath = slurper.parse(yamlPath) as Map
        Map fromFile = slurper.parse(yamlPath.toFile()) as Map

        [fromReader, fromStream, fromPath, fromFile].each { Map document ->
            Map service = document['service'] as Map
            assertThat(service['name']).isEqualTo('inventory')
            assertThat(service['ports']).containsExactly(8080, 8081)
            assertThat(service['labels']).containsEntry('tier', 'backend')
        }
    }

    @Test
    void parsesMultipleYamlDocumentsAsAList() {
        String yaml = '''\
---
name: first
value: 1
---
name: second
value: 2
---
- alpha
- beta
'''

        List documents = new YamlSlurper().parseText(yaml) as List

        assertThat(documents).hasSize(3)
        assertThat(documents[0]).containsEntry('name', 'first')
        assertThat(documents[1]).containsEntry('value', 2)
        assertThat(documents[2]).containsExactly('alpha', 'beta')
    }

    @Test
    void buildsYamlFromMapListVarargsAndWritesToAWriter() {
        YamlBuilder mapBuilder = new YamlBuilder()
        Map mapContent = mapBuilder(
                application: 'catalog',
                enabled: true,
                limits: [cpu: 2, memory: '512Mi'],
                owners: ['platform', 'runtime']) as Map

        assertThat(mapContent).containsEntry('application', 'catalog')
        assertThat(mapContent['limits']).containsEntry('cpu', 2)
        assertThat((parseYaml(mapBuilder) as Map)['owners']).containsExactly('platform', 'runtime')

        StringWriter writer = new StringWriter()
        assertThat(mapBuilder.writeTo(writer)).isSameAs(writer)
        assertThat(new YamlSlurper().parseText(writer.toString())).isEqualTo(mapBuilder.content)

        YamlBuilder listBuilder = new YamlBuilder()
        List listContent = listBuilder([[name: 'one'], [name: 'two']]) as List
        assertThat(listContent).hasSize(2)
        assertThat(parseYaml(listBuilder)[1]).containsEntry('name', 'two')

        YamlBuilder varargsBuilder = new YamlBuilder()
        List varargsContent = varargsBuilder('red', 'green', 'blue') as List
        assertThat(varargsContent).containsExactly('red', 'green', 'blue')
        assertThat(parseYaml(varargsBuilder)).containsExactly('red', 'green', 'blue')
    }

    @Test
    void buildsNestedYamlWithClosuresAndDynamicRootMethods() {
        YamlBuilder closureBuilder = new YamlBuilder()
        Map closureContent = closureBuilder {
            title 'Native image smoke test'
            details attempts: 2, stable: true
            tags 'groovy', 'yaml', 'builder'
        } as Map

        assertThat(closureContent['details']).containsEntry('attempts', 2)
        Map closureYaml = parseYaml(closureBuilder) as Map
        assertThat(closureYaml['title']).isEqualTo('Native image smoke test')
        assertThat(closureYaml['details']).containsEntry('stable', true)
        assertThat(closureYaml['tags']).containsExactly('groovy', 'yaml', 'builder')

        YamlBuilder rootMethodBuilder = new YamlBuilder()
        Map rootContent = rootMethodBuilder.pipeline(name: 'original', timeoutSeconds: 30) {
            name 'release'
            stages 'compile', 'test', 'package'
        } as Map

        assertThat(rootContent).containsOnlyKeys('pipeline')
        Map pipeline = (parseYaml(rootMethodBuilder) as Map)['pipeline'] as Map
        assertThat(pipeline['name']).isEqualTo('release')
        assertThat(pipeline['timeoutSeconds']).isEqualTo(30)
        assertThat(pipeline['stages']).containsExactly('compile', 'test', 'package')
    }

    @Test
    void buildsYamlArrayByTransformingAnIterableWithAClosure() {
        List<Map<String, Object>> services = [
                [name: 'api', replicas: 2, ports: [8080, 8443]],
                [name: 'worker', replicas: 1, ports: [9000]]
        ]
        YamlBuilder builder = new YamlBuilder()

        List content = builder(services) { Map<String, Object> service ->
            name service['name']
            replicas service['replicas']
            firstPort service['ports'][0]
        } as List

        assertThat(content).hasSize(2)
        List parsed = parseYaml(builder) as List
        assertThat(parsed[0]).containsEntry('name', 'api')
        assertThat(parsed[0]).containsEntry('firstPort', 8080)
        assertThat(parsed[1]).containsEntry('replicas', 1)
    }

    @Test
    void convertsYamlAndJsonWithConverterUtilities() {
        String json = YamlConverter.convertYamlToJson(new StringReader('''\
---
items:
  - id: 1
    label: first
  - id: 2
    label: second
'''))

        Map parsedJson = new JsonSlurper().parseText(json) as Map
        assertThat(parsedJson.items).hasSize(2)
        assertThat(parsedJson.items[0]).containsEntry('label', 'first')

        String yaml = YamlConverter.convertJsonToYaml(new StringReader('''\
{
  "project": "reachability",
  "features": ["parse", "build", "convert"],
  "nested": {"enabled": true}
}
'''))

        Map parsedYaml = new YamlSlurper().parseText(yaml) as Map
        assertThat(parsedYaml['project']).isEqualTo('reachability')
        assertThat(parsedYaml['features']).containsExactly('parse', 'build', 'convert')
        assertThat(parsedYaml['nested']).containsEntry('enabled', true)
    }

    @Test
    void reportsMalformedYamlAndJsonParseFailures() {
        assertThatThrownBy { new YamlSlurper().parseText('key: [unterminated') }
                .isInstanceOf(IOException)
                .hasMessageContaining('expected')

        assertThatThrownBy { YamlConverter.convertJsonToYaml(new StringReader('{"key":')) }
                .isInstanceOf(IOException)
                .hasMessageContaining('Unexpected end-of-input')
    }

    private static Object parseYaml(YamlBuilder builder) {
        String yaml = builder.toString()

        assertThat(yaml).startsWith('---')
        new YamlSlurper().parseText(yaml)
    }
}
