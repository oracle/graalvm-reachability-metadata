/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jayway_jsonpath.json_path_assert;

import com.jayway.jsonassert.JsonAssert;
import com.jayway.jsonassert.JsonAsserter;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.jayway.jsonassert.JsonAssert.collectionWithSize;
import static com.jayway.jsonassert.JsonAssert.emptyCollection;
import static com.jayway.jsonassert.JsonAssert.mapContainingKey;
import static com.jayway.jsonassert.JsonAssert.mapContainingValue;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.isJson;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.isJsonFile;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.isJsonString;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withoutJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertSame;

public class JsonPathAssertTest {
    private static final String JSON =
            """
            {
              "name": "Ada",
              "age": 37,
              "middleName": null,
              "tags": ["native", "json"],
              "empty": [],
              "profile": {"role": "engineer", "level": 3}
            }
            """;

    @Test
    void matchesJsonPathsOnStringsObjectsAndReadContexts() {
        assertThat(JSON, isJson());
        assertThat("[1, 2, 3]", isJson());
        assertThat("not-json", not(isJson()));
        assertThat(JSON, hasJsonPath("$.name"));
        assertThat(JSON, hasJsonPath("$.age", greaterThan(30)));
        assertThat(JSON, hasNoJsonPath("$.address"));

        ReadContext context = JsonPath.parse(JSON);
        JsonPath namePath = JsonPath.compile("$.name");
        JsonPath missingPath = JsonPath.compile("$.address.city");

        assertThat(context, withJsonPath("$.tags"));
        assertThat(context, withJsonPath(namePath));
        assertThat(context, withJsonPath("$.name", equalTo("Ada")));
        assertThat(context, withJsonPath(namePath, equalTo("Ada")));
        assertThat(context, withoutJsonPath("$.unknown"));
        assertThat(context, withoutJsonPath(missingPath));
        assertThat(context, not(withJsonPath("$.name", equalTo("Grace"))));
        assertThat(context, not(withoutJsonPath(namePath)));

        Object parsedJson = context.json();
        assertThat(parsedJson, isJson(withJsonPath("$.profile.level", equalTo(3))));
        assertThat(context, isJson(withJsonPath("$.tags", contains("native", "json"))));
        assertThat(JSON, isJsonString(withJsonPath("$.name", equalTo("Ada"))));
    }

    @Test
    void matchesJsonLoadedFromAFile(@TempDir Path temporaryDirectory) throws Exception {
        Path jsonFile = temporaryDirectory.resolve("document.json");
        Files.writeString(jsonFile, JSON, StandardCharsets.UTF_8);

        File file = jsonFile.toFile();
        assertThat(file, isJsonFile(withJsonPath("$.profile.role", equalTo("engineer"))));
        assertThat(file, hasJsonPath("$.empty"));
        assertThat(file, hasNoJsonPath("$.profile.department"));
    }

    @Test
    void fluentlyAssertsJsonFromAString() {
        JsonAsserter asserter = JsonAssert.with(JSON);

        JsonAsserter result = asserter.assertEquals("$.name", "Ada")
                .and()
                .assertEquals("$.age", 37, "age should be preserved")
                .assertThat("$.tags", collectionWithSize(equalTo(2)))
                .assertThat("$.tags", contains("native", "json"), "tags should retain order")
                .assertThat("$.profile", mapContainingKey(equalTo("role")))
                .assertThat("$.profile", mapContainingValue(equalTo("engineer")))
                .assertThat("$.empty", emptyCollection())
                .assertNull("$.middleName")
                .assertNull("$.middleName", "middle name should remain null")
                .assertNotNull("$.profile")
                .assertNotNull("$.profile.role", "role should be present")
                .assertNotDefined("$.address")
                .assertNotDefined("$.profile.department", "department should be absent");

        assertSame(asserter, result);
    }

    @Test
    void fluentlyAssertsJsonFromReaderAndInputStream() throws Exception {
        JsonAsserter readerAsserter = JsonAssert.with(new StringReader(JSON));
        JsonAsserter streamAsserter = JsonAssert.with(
                new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)));

        assertSame(readerAsserter, readerAsserter.assertEquals("$.profile.level", 3));
        assertSame(streamAsserter, streamAsserter.assertThat("$.name", is("Ada")));
    }
}
