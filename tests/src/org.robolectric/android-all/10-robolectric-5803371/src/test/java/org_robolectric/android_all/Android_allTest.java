/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_robolectric.android_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import android.content.ContentValues;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Pair;
import android.util.Patterns;
import android.util.Range;
import android.util.Size;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Android_allTest {
    @Test
    void uriParsesBuildsEncodesAndNormalizesHierarchicalUris() {
        Uri parsed = Uri.parse("HTTP://user@example.com:8080/a%20path/item?name=Jane%20Doe&debug=true&debug=false#frag");

        assertThat(parsed.isAbsolute()).isTrue();
        assertThat(parsed.isHierarchical()).isTrue();
        assertThat(parsed.getScheme()).isEqualTo("HTTP");
        assertThat(parsed.normalizeScheme().getScheme()).isEqualTo("http");
        assertThat(parsed.getUserInfo()).isEqualTo("user");
        assertThat(parsed.getHost()).isEqualTo("example.com");
        assertThat(parsed.getPort()).isEqualTo(8080);
        assertThat(parsed.getPathSegments()).containsExactly("a path", "item");
        assertThat(parsed.getLastPathSegment()).isEqualTo("item");
        assertThat(parsed.getQueryParameter("name")).isEqualTo("Jane Doe");
        assertThat(parsed.getQueryParameters("debug")).containsExactly("true", "false");
        assertThat(parsed.getBooleanQueryParameter("debug", false)).isTrue();
        assertThat(parsed.getFragment()).isEqualTo("frag");

        Uri rebuilt = parsed.buildUpon()
                .scheme("https")
                .clearQuery()
                .appendQueryParameter("city", "New York")
                .appendQueryParameter("empty", "")
                .build();

        assertThat(rebuilt.toString())
                .isEqualTo("https://user@example.com:8080/a%20path/item?city=New%20York&empty=#frag");
        Uri appended = Uri.withAppendedPath(Uri.parse("content://authority/root"), "child value");
        assertThat(appended.toString()).isEqualTo("content://authority/root/child value");
        assertThat(appended.getLastPathSegment()).isEqualTo("child value");
        assertThat(Uri.decode(Uri.encode("a value/with spaces", "/"))).isEqualTo("a value/with spaces");
    }

    @Test
    void bundleStoresCopiesAndRemovesTypedValues() {
        Bundle nested = Bundle.forPair("nested", "value");
        Bundle bundle = new Bundle();
        ArrayList<String> names = new ArrayList<>(Arrays.asList("alpha", "beta"));

        bundle.putString("name", "robolectric");
        bundle.putInt("api", 29);
        bundle.putBoolean("enabled", true);
        bundle.putFloat("ratio", 1.5F);
        bundle.putStringArrayList("names", names);
        bundle.putBundle("nested", nested);
        bundle.putSize("size", new Size(1920, 1080));

        assertThat(bundle.containsKey("api")).isTrue();
        assertThat(bundle.getString("name")).isEqualTo("robolectric");
        assertThat(bundle.getInt("api")).isEqualTo(29);
        assertThat(bundle.getBoolean("enabled")).isTrue();
        assertThat(bundle.getFloat("ratio")).isEqualTo(1.5F);
        assertThat(bundle.getStringArrayList("names")).containsExactly("alpha", "beta");
        assertThat(bundle.getBundle("nested").getString("nested")).isEqualTo("value");
        assertThat(bundle.getSize("size")).isEqualTo(new Size(1920, 1080));
        assertThat(bundle.getString("missing", "fallback")).isEqualTo("fallback");

        Bundle deepCopy = bundle.deepCopy();
        bundle.getBundle("nested").putString("nested", "changed");
        bundle.getStringArrayList("names").add("gamma");
        bundle.remove("api");

        assertThat(deepCopy.getBundle("nested").getString("nested")).isEqualTo("value");
        assertThat(deepCopy.getStringArrayList("names")).containsExactly("alpha", "beta");
        assertThat(bundle.containsKey("api")).isFalse();
    }

    @Test
    void contentValuesPreservesTypesCopiesValuesAndExposesKeys() {
        ContentValues values = new ContentValues();
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);

        values.put("title", "Android");
        values.put("count", 7);
        values.put("price", 12.5D);
        values.put("active", true);
        values.put("blob", payload);
        values.putNull("nullable");

        assertThat(values.size()).isEqualTo(6);
        assertThat(values.getAsString("title")).isEqualTo("Android");
        assertThat(values.getAsInteger("count")).isEqualTo(7);
        assertThat(values.getAsLong("count")).isEqualTo(7L);
        assertThat(values.getAsDouble("price")).isEqualTo(12.5D);
        assertThat(values.getAsBoolean("active")).isTrue();
        assertThat(values.getAsByteArray("blob")).containsExactly(payload);
        assertThat(values.containsKey("nullable")).isTrue();
        assertThat(values.get("nullable")).isNull();

        ContentValues copy = new ContentValues(values);
        values.remove("title");
        values.clear();

        assertThat(copy.keySet()).containsExactlyInAnyOrder("title", "count", "price", "active", "blob", "nullable");
        assertThat(copy.valueSet()).extracting(Map.Entry::getKey).contains("title", "count", "price");
        assertThat(values.isEmpty()).isTrue();
    }

    @Test
    void textUtilsHandlesNullEmptyJoiningAndSplitting() {
        assertThat(TextUtils.isEmpty(null)).isTrue();
        assertThat(TextUtils.isEmpty("")).isTrue();
        assertThat(TextUtils.isEmpty("android")).isFalse();
        assertThat(TextUtils.emptyIfNull(null)).isEmpty();
        assertThat(TextUtils.emptyIfNull("value")).isEqualTo("value");
        assertThat(TextUtils.nullIfEmpty("")).isNull();
        assertThat(TextUtils.nullIfEmpty("value")).isEqualTo("value");
        assertThat(TextUtils.firstNotEmpty("", "fallback")).isEqualTo("fallback");
        assertThat(TextUtils.length(null)).isZero();
        assertThat(TextUtils.length("android-all")).isEqualTo(11);
        assertThat(TextUtils.equals("metadata", new StringBuilder("metadata"))).isTrue();
        assertThat(TextUtils.equals("metadata", "different")).isFalse();
        assertThat(TextUtils.join("/", Arrays.asList("android", "all", "10"))).isEqualTo("android/all/10");
        assertThat(TextUtils.join(",", new Object[] {"Uri", "Bundle", "Rect"})).isEqualTo("Uri,Bundle,Rect");
        assertThat(TextUtils.split("Uri,Bundle,Rect", ",")).containsExactly("Uri", "Bundle", "Rect");
    }

    @Test
    void arrayMapBehavesAsOrderedMapWithIndexOperations() {
        ArrayMap<String, Integer> map = new ArrayMap<>();
        map.put("one", 1);
        map.put("two", 2);
        map.append("three", 3);

        assertThat(map).containsEntry("one", 1).containsEntry("two", 2).containsEntry("three", 3);
        assertThat(map.keyAt(0)).isEqualTo("one");
        assertThat(map.valueAt(map.indexOfKey("two"))).isEqualTo(2);
        assertThat(map.setValueAt(map.indexOfKey("two"), 22)).isEqualTo(2);
        assertThat(map.removeAt(map.indexOfKey("one"))).isEqualTo(1);

        ArrayMap<String, Integer> copy = new ArrayMap<>(map);
        copy.putAll(Map.of("four", 4, "five", 5));

        assertThat(copy).containsEntry("two", 22).containsEntry("four", 4).containsEntry("five", 5);
        assertThat(copy.containsAll(Arrays.asList("two", "three"))).isTrue();
        assertThat(copy.removeAll(Arrays.asList("three", "five"))).isTrue();
        assertThat(copy.keySet()).containsExactlyInAnyOrder("two", "four");
        assertThat(map.keySet()).containsExactlyInAnyOrder("two", "three");
    }

    @Test
    void pairCreatesValueObjectsWithReadableMembersAndEquality() {
        Pair<String, Integer> direct = new Pair<>("api", 29);
        Pair<String, Integer> created = Pair.create("api", 29);
        Pair<String, Integer> different = Pair.create("api", 30);

        assertThat(direct.first).isEqualTo("api");
        assertThat(direct.second).isEqualTo(29);
        assertThat(direct).isEqualTo(created).hasSameHashCodeAs(created);
        assertThat(direct).isNotEqualTo(different);
        assertThat(direct.toString()).contains("api", "29");
        assertThat(Pair.create(null, "value")).isEqualTo(new Pair<>(null, "value"));
    }

    @Test
    void rangeAndSizeSupportParsingContainmentClampingAndSetOperations() {
        Range<Integer> range = Range.create(10, 20);

        assertThat(range.contains(10)).isTrue();
        assertThat(range.contains(15)).isTrue();
        assertThat(range.contains(Range.create(12, 18))).isTrue();
        assertThat(range.clamp(5)).isEqualTo(10);
        assertThat(range.clamp(25)).isEqualTo(20);
        assertThat(range.intersect(15, 30)).isEqualTo(Range.create(15, 20));
        assertThat(range.extend(0, 12)).isEqualTo(Range.create(0, 20));
        assertThat(range.extend(25)).isEqualTo(Range.create(10, 25));
        assertThat(range.toString()).isEqualTo("[10, 20]");

        Size parsedWithX = Size.parseSize("640x480");
        Size parsedWithStar = Size.parseSize("800*600");
        assertThat(parsedWithX.getWidth()).isEqualTo(640);
        assertThat(parsedWithX.getHeight()).isEqualTo(480);
        assertThat(parsedWithStar.toString()).isEqualTo("800x600");
        assertThatThrownBy(() -> Range.create(20, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Size.parseSize("not-a-size")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void rectCalculatesGeometryIntersectionsUnionsAndStringRoundTrips() {
        Rect rect = new Rect(10, 20, 50, 80);

        assertThat(rect.width()).isEqualTo(40);
        assertThat(rect.height()).isEqualTo(60);
        assertThat(rect.centerX()).isEqualTo(30);
        assertThat(rect.centerY()).isEqualTo(50);
        assertThat(rect.exactCenterX()).isEqualTo(30.0F);
        assertThat(rect.exactCenterY()).isEqualTo(50.0F);
        assertThat(rect.contains(10, 20)).isTrue();
        assertThat(rect.contains(50, 80)).isFalse();
        assertThat(rect.contains(new Rect(15, 25, 45, 75))).isTrue();

        Rect intersection = new Rect(rect);
        assertThat(intersection.intersect(new Rect(40, 70, 100, 120))).isTrue();
        assertThat(intersection).isEqualTo(new Rect(40, 70, 50, 80));
        assertThat(Rect.intersects(rect, new Rect(49, 79, 60, 90))).isTrue();

        rect.offset(5, -10);
        rect.inset(5, 5);
        rect.union(0, 0, 12, 12);
        assertThat(rect).isEqualTo(new Rect(0, 0, 50, 65));
        assertThat(Rect.unflattenFromString(rect.flattenToString())).isEqualTo(rect);

        Rect unsorted = new Rect(8, 9, 1, 2);
        unsorted.sort();
        assertThat(unsorted).isEqualTo(new Rect(1, 2, 8, 9));
    }

    @Test
    void base64EncodesDecodesDefaultNoWrapAndUrlSafeVariants() {
        byte[] data = "Robolectric Android APIs".getBytes(StandardCharsets.UTF_8);

        String defaultEncoded = Base64.encodeToString(data, Base64.DEFAULT);
        assertThat(defaultEncoded).endsWith("\n");
        assertThat(new String(Base64.decode(defaultEncoded, Base64.DEFAULT), StandardCharsets.UTF_8))
                .isEqualTo("Robolectric Android APIs");

        String noWrapEncoded = Base64.encodeToString(data, Base64.NO_WRAP | Base64.NO_PADDING);
        assertThat(noWrapEncoded).doesNotContain("\n").doesNotEndWith("=");
        assertThat(Base64.decode(noWrapEncoded, Base64.NO_WRAP | Base64.NO_PADDING)).containsExactly(data);

        byte[] urlData = new byte[] {(byte) 0xFB, (byte) 0xEF, (byte) 0xFF};
        String urlEncoded = Base64.encodeToString(urlData, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        assertThat(urlEncoded).isEqualTo("--__");
        assertThat(Base64.decode(urlEncoded, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)).containsExactly(urlData);
    }

    @Test
    void jsonWriterAndReaderStreamNestedDocumentsAndSkipValues() throws IOException {
        StringWriter stringWriter = new StringWriter();
        JsonWriter writer = new JsonWriter(stringWriter);
        writer.setIndent("  ");
        writer.beginObject();
        writer.name("name").value("android-all");
        writer.name("version").value(10L);
        writer.name("supported").value(true);
        writer.name("unused").beginObject().name("skipMe").value("ignored").endObject();
        writer.name("apis").beginArray().value("Uri").value("Bundle").nullValue().endArray();
        writer.endObject();
        writer.close();

        JsonReader reader = new JsonReader(new StringReader(stringWriter.toString()));
        List<String> apis = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                assertThat(reader.nextString()).isEqualTo("android-all");
            } else if (name.equals("version")) {
                assertThat(reader.nextInt()).isEqualTo(10);
            } else if (name.equals("supported")) {
                assertThat(reader.nextBoolean()).isTrue();
            } else if (name.equals("unused")) {
                reader.skipValue();
            } else if (name.equals("apis")) {
                reader.beginArray();
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                    } else {
                        apis.add(reader.nextString());
                    }
                }
                reader.endArray();
            }
        }
        reader.endObject();
        reader.close();

        assertThat(apis).containsExactly("Uri", "Bundle");
    }

    @Test
    void patternsMatchCommonAndroidTextInputs() {
        assertThat(Patterns.EMAIL_ADDRESS.matcher("developer@example.com").matches()).isTrue();
        assertThat(Patterns.EMAIL_ADDRESS.matcher("not an email").matches()).isFalse();
        assertThat(Patterns.WEB_URL.matcher("https://developer.android.com/reference/android/net/Uri").matches()).isTrue();
        assertThat(Patterns.IP_ADDRESS.matcher("192.168.0.1").matches()).isTrue();
        assertThat(Patterns.IP_ADDRESS.matcher("999.168.0.1").matches()).isFalse();
        assertThat(Patterns.PHONE.matcher("+1 (650) 253-0000").matches()).isTrue();
    }
}
