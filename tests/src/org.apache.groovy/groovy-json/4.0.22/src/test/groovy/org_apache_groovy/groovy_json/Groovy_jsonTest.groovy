/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_json

import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonGenerator
import groovy.json.JsonLexer
import groovy.json.JsonOutput
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic
import groovy.json.JsonToken
import groovy.json.JsonTokenType
import groovy.json.StreamingJsonBuilder
import groovy.util.Expando
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Date
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.UUID

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

public class Groovy_jsonTest {
    @Test
    void parsesStrictJsonWithEachSlurperParserType() {
        String json = '''{
            "name":"Groovy",
            "numbers":[1,2.5,-3],
            "enabled":true,
            "missing":null,
            "nested":{"escaped":"line\\nbreak","unicode":"☕"}
        }'''

        JsonParserType.values().each { JsonParserType parserType ->
            JsonSlurper slurper = new JsonSlurper()
                    .setType(parserType)
                    .setChop(true)
                    .setLazyChop(false)
                    .setCheckDates(false)
            Object parsed = slurper.parseText(json)

            assertThat(slurper.type).isEqualTo(parserType)
            assertThat(slurper.chop).isTrue()
            assertThat(slurper.lazyChop).isFalse()
            assertThat(slurper.checkDates).isFalse()
            assertThat(parsed.name).isEqualTo('Groovy')
            assertThat(parsed.numbers).containsExactly(1, 2.5, -3)
            assertThat(parsed.enabled).isTrue()
            assertThat(parsed.missing).isNull()
            assertThat(parsed.nested.escaped).isEqualTo('line\nbreak')
            assertThat(parsed.nested.unicode).isEqualTo('☕')
        }
    }

    @Test
    void laxSlurperParsesRelaxedJsonSyntax() {
        String relaxedJson = '''
            // LAX mode accepts comments, unquoted object keys, and single-quoted strings.
            {
                name: 'Groovy',
                enabled: true,
                nested: {
                    description: 'relaxed syntax',
                    count: 3
                },
                tags: [
                    'json',
                    'lax'
                ]
            }
        '''

        Object parsed = new JsonSlurper()
                .setType(JsonParserType.LAX)
                .parseText(relaxedJson)

        assertThat(parsed.name).isEqualTo('Groovy')
        assertThat(parsed.enabled).isTrue()
        assertThat(parsed.nested.description).isEqualTo('relaxed syntax')
        assertThat(parsed.nested.count).isEqualTo(3)
        assertThat(parsed.tags).containsExactly('json', 'lax')
    }

    @Test
    void parsesJsonFromReadersStreamsArraysFilesPathsAndUrls() {
        String json = '{"greeting":"héllo","values":[1,2,3],"nested":{"ok":true}}'
        JsonSlurper slurper = new JsonSlurper()
        Path tempFile = Files.createTempFile('groovy-json-', '.json')

        try {
            Files.writeString(tempFile, json, StandardCharsets.UTF_8)

            assertParsedDocument(slurper.parse(new StringReader(json)))
            assertParsedDocument(slurper.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))))
            assertParsedDocument(slurper.parse(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), 'UTF-8'))
            assertParsedDocument(slurper.parse(json.getBytes(StandardCharsets.UTF_8)))
            assertParsedDocument(slurper.parse(json.getBytes(StandardCharsets.UTF_8), 'UTF-8'))
            assertParsedDocument(slurper.parse(json.toCharArray()))
            assertParsedDocument(slurper.parse(tempFile))
            assertParsedDocument(slurper.parse(tempFile, 'UTF-8'))
            assertParsedDocument(slurper.parse(tempFile.toFile()))
            assertParsedDocument(slurper.parse(tempFile.toFile(), 'UTF-8'))
            assertParsedDocument(slurper.parse(tempFile.toUri().toURL()))
            assertParsedDocument(slurper.parse(tempFile.toUri().toURL(), [useCaches: false], 'UTF-8'))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    void serializesCommonJsonOutputTypesAndRejectsInvalidJsonNumbers() {
        UUID uuid = UUID.fromString('123e4567-e89b-12d3-a456-426614174000')
        URL url = URI.create('https://groovy-lang.org/json.html').toURL()
        Map<String, Object> payload = [
                text      : 'quote " slash \\ newline\n unicode ☕',
                truth     : true,
                count     : 7,
                decimal   : 2.75G,
                uuid      : uuid,
                url       : url,
                raw       : JsonOutput.unescaped('{"trusted":true}'),
                expando   : new Expando(name: 'dynamic', active: true),
                closure   : { label 'from closure' },
                state     : Thread.State.RUNNABLE,
                intArray  : [1, 2, 3] as int[],
                charArray : ['a' as char, '\n' as char] as char[],
                enumeration: Collections.enumeration(['first', 'second'])
        ]

        String json = JsonOutput.toJson(payload)
        Object parsed = new JsonSlurper().parseText(json)

        assertThat(parsed.text).isEqualTo('quote " slash \\ newline\n unicode ☕')
        assertThat(parsed.truth).isTrue()
        assertThat(parsed.count).isEqualTo(7)
        assertThat(parsed.decimal).isEqualByComparingTo(2.75G)
        assertThat(parsed.uuid).isEqualTo(uuid.toString())
        assertThat(parsed.url).isEqualTo(url.toString())
        assertThat(parsed.raw.trusted).isTrue()
        assertThat(parsed.expando.name).isEqualTo('dynamic')
        assertThat(parsed.expando.active).isTrue()
        assertThat(parsed.closure.label).isEqualTo('from closure')
        assertThat(parsed.state).isEqualTo('RUNNABLE')
        assertThat(parsed.intArray).containsExactly(1, 2, 3)
        assertThat(parsed.charArray).containsExactly('a', '\n')
        assertThat(parsed.enumeration).containsExactly('first', 'second')
        assertThat(JsonOutput.toJson(null as Object)).isEqualTo('null')
        assertThat(JsonOutput.toJson('x' as Character)).isEqualTo('"x"')

        assertThatThrownBy { JsonOutput.toJson(Double.NaN) }
                .isInstanceOf(JsonException)
                .hasMessageContaining('NaN')
        assertThatThrownBy { JsonOutput.toJson([(null): 'value']) }
                .isInstanceOf(IllegalArgumentException)
                .hasMessageContaining('null keys')
    }

    @Test
    void customJsonGeneratorHonorsConvertersExclusionsDatesAndUnicode() {
        UUID uuid = UUID.fromString('123e4567-e89b-12d3-a456-426614174000')
        JsonGenerator generator = new JsonGenerator.Options()
                .excludeNulls()
                .disableUnicodeEscaping()
                .dateFormat('yyyy-MM-dd', Locale.US)
                .timezone('GMT')
                .excludeFieldsByName('secret')
                .excludeFieldsByType(URL)
                .addConverter(UUID) { UUID value, String key ->
                    "${key}:${value.toString().substring(0, 8)}"
                }
                .build()

        Map<String, Object> input = [
                keep   : 'žluťoučký kůň',
                missing: null,
                secret : 'hidden',
                site   : URI.create('https://example.com').toURL(),
                id     : uuid,
                date   : new Date(0L),
                nested : [skip: null, ok: 1],
                list   : [null, URI.create('https://example.org').toURL(), 'ok']
        ]

        String json = generator.toJson(input)
        Object parsed = new JsonSlurper().parseText(json)

        assertThat(generator.isExcludingFieldsNamed('secret')).isTrue()
        assertThat(generator.isExcludingValues(null)).isTrue()
        assertThat(generator.isExcludingValues(URI.create('https://example.net').toURL())).isTrue()
        assertThat(json).contains('žluťoučký kůň')
        assertThat(parsed.keep).isEqualTo('žluťoučký kůň')
        assertThat(parsed).doesNotContainKeys('missing', 'secret', 'site')
        assertThat(parsed.id).isEqualTo('id:123e4567')
        assertThat(parsed.date).isEqualTo('1970-01-01')
        assertThat(parsed.nested).doesNotContainKey('skip')
        assertThat(parsed.nested.ok).isEqualTo(1)
        assertThat(parsed.list).containsExactly('ok')
    }

    @Test
    void buildsJsonDocumentsWithJsonBuilder() {
        JsonBuilder builder = new JsonBuilder()
        Object content = builder.catalog {
            title 'Groovy JSON'
            active true
            books([1, 2]) { Integer bookId ->
                identifier bookId
                available bookId == 1
            }
            tags 'parser', 'writer'
            metadata(createdBy: 'JsonBuilder', version: 4)
        }

        String json = builder.toString()
        String prettyJson = builder.toPrettyString()
        StringWriter written = new StringWriter()
        builder.writeTo(written)
        Object parsed = new JsonSlurper().parseText(json)

        assertThat(content.catalog.title).isEqualTo('Groovy JSON')
        assertThat(written.toString()).isEqualTo(json)
        assertThat(prettyJson).contains('\n    "catalog"')
        assertThat(parsed.catalog.title).isEqualTo('Groovy JSON')
        assertThat(parsed.catalog.active).isTrue()
        assertThat(parsed.catalog.books*.identifier).containsExactly(1, 2)
        assertThat(parsed.catalog.books*.available).containsExactly(true, false)
        assertThat(parsed.catalog.tags).containsExactly('parser', 'writer')
        assertThat(parsed.catalog.metadata.createdBy).isEqualTo('JsonBuilder')
        assertThat(parsed.catalog.metadata.version).isEqualTo(4)
    }

    @Test
    void streamsJsonDocumentsWithStreamingJsonBuilder() {
        StringWriter writer = new StringWriter()
        StreamingJsonBuilder builder = new StreamingJsonBuilder(writer)
        List<Map<String, String>> peopleRows = [
                [name: 'Ada', language: 'Groovy'],
                [name: 'Grace', language: 'COBOL']
        ]

        builder.call {
            message 'streamed'
            answer 42
            raw JsonOutput.unescaped('{"trusted":true}')
            people(peopleRows) { Map<String, String> person ->
                name person.name
                language person.language
            }
            values 1, 2, 3
        }

        Object parsed = new JsonSlurper().parseText(writer.toString())

        assertThat(parsed.message).isEqualTo('streamed')
        assertThat(parsed.answer).isEqualTo(42)
        assertThat(parsed.raw.trusted).isTrue()
        assertThat(parsed.people*.name).containsExactly('Ada', 'Grace')
        assertThat(parsed.people*.language).containsExactly('Groovy', 'COBOL')
        assertThat(parsed['values']).containsExactly(1, 2, 3)
    }

    @Test
    void prettyPrintsLexesAndUnescapesJson() {
        String compactJson = '{"message":"Привет","items":[1,{"x":true}]}'

        String prettyJson = JsonOutput.prettyPrint(compactJson, true)
        List<JsonToken> tokens = new JsonLexer(new StringReader(compactJson)).collect { JsonToken token -> token }

        assertThat(prettyJson).contains('"message": "Привет"')
        assertThat(prettyJson).contains('\n    "items": [')
        assertThat(tokens*.type).contains(
                JsonTokenType.OPEN_CURLY,
                JsonTokenType.STRING,
                JsonTokenType.COLON,
                JsonTokenType.OPEN_BRACKET,
                JsonTokenType.TRUE,
                JsonTokenType.CLOSE_CURLY)
        assertThat(tokens.find { it.type == JsonTokenType.STRING }.value).isEqualTo('message')
        assertThat(JsonLexer.unescape('line\\nvalue\\u0021')).isEqualTo('line\nvalue!')
    }

    @Test
    void classicSlurperParsesObjectsArraysAndNulls() {
        String json = '{"value":1,"items":[true,false,null],"nested":{"name":"classic"}}'
        Object parsed = new JsonSlurperClassic().parseText(json)

        assertThat(parsed.value).isEqualTo(1)
        assertThat(parsed.items).containsExactly(true, false, null)
        assertThat(parsed.nested.name).isEqualTo('classic')

        assertThatThrownBy { new JsonSlurper().parseText('') }
                .isInstanceOf(IllegalArgumentException)
                .hasMessageContaining('Text must not be null or empty')
    }

    private static void assertParsedDocument(Object parsed) {
        assertThat(parsed.greeting).isEqualTo('héllo')
        assertThat(parsed['values']).containsExactly(1, 2, 3)
        assertThat(parsed.nested.ok).isTrue()
    }
}
