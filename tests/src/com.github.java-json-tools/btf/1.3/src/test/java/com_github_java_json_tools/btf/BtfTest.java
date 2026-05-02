/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_java_json_tools.btf;

import com.github.fge.Builder;
import com.github.fge.Frozen;
import com.github.fge.Thawed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BtfTest {
    @Test
    void builderBuildsImmutableSnapshots() {
        DocumentBuilder builder = new DocumentBuilder()
                .title("draft")
                .addTag("json")
                .addTag("native-image");

        Document firstSnapshot = build(builder);

        builder.title("published")
                .addTag("reachability");
        Document secondSnapshot = builder.build();

        assertThat(firstSnapshot.title()).isEqualTo("draft");
        assertThat(firstSnapshot.tags()).containsExactly("json", "native-image");
        assertThat(secondSnapshot.title()).isEqualTo("published");
        assertThat(secondSnapshot.tags()).containsExactly("json", "native-image", "reachability");
        assertThatThrownBy(() -> firstSnapshot.tags().add("mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void frozenObjectsThawIntoIndependentMutableCopies() {
        Document original = new DocumentBuilder()
                .title("original")
                .addTag("stable")
                .freeze();

        DocumentBuilder thawed = thaw(original);
        thawed.title("edited")
                .addTag("copy");
        Document edited = freeze(thawed);

        assertThat(original.title()).isEqualTo("original");
        assertThat(original.tags()).containsExactly("stable");
        assertThat(edited.title()).isEqualTo("edited");
        assertThat(edited.tags()).containsExactly("stable", "copy");
    }

    @Test
    void publicGenericContractsComposeThroughInterfaces() {
        Builder<Document> builder = new DocumentBuilder()
                .title("contract")
                .addTag("builder");

        Document built = builder.build();
        Frozen<DocumentBuilder> frozen = built;
        Thawed<Document> thawed = frozen.thaw();

        Document refrozen = thawed.freeze();

        assertThat(refrozen).isNotSameAs(built);
        assertThat(refrozen.title()).isEqualTo("contract");
        assertThat(refrozen.tags()).containsExactly("builder");
    }

    @Test
    void buildersCanCreateValuesWithoutFreezeThawLifecycle() {
        SearchQuery query = buildSearchQuery(new SearchQueryBuilder()
                .term("native image")
                .includeField("title")
                .includeField("summary"));

        assertThat(query.term()).isEqualTo("native image");
        assertThat(query.fields()).containsExactly("title", "summary");
    }

    @Test
    void frozenValuesCanExposeNarrowPublicEditorInterfaces() {
        Profile profile = new Profile("reader", List.of("read"));

        ProfileEditor editor = openProfileEditor(profile);
        editor.rename("maintainer")
                .allow("write")
                .allow("release");
        Profile updated = editor.freeze();

        assertThat(updated.name()).isEqualTo("maintainer");
        assertThat(updated.permissions()).containsExactly("read", "write", "release");
    }

    private static Document build(Builder<Document> builder) {
        return builder.build();
    }

    private static SearchQuery buildSearchQuery(Builder<SearchQuery> builder) {
        return builder.build();
    }

    private static DocumentBuilder thaw(Frozen<DocumentBuilder> frozen) {
        return frozen.thaw();
    }

    private static Document freeze(Thawed<Document> thawed) {
        return thawed.freeze();
    }

    private static ProfileEditor openProfileEditor(Frozen<ProfileEditor> frozen) {
        return frozen.thaw();
    }

    private static final class Document implements Frozen<DocumentBuilder> {
        private final String title;
        private final List<String> tags;

        private Document(String title, List<String> tags) {
            this.title = title;
            this.tags = List.copyOf(tags);
        }

        @Override
        public DocumentBuilder thaw() {
            return new DocumentBuilder()
                    .title(title)
                    .addTags(tags);
        }

        private String title() {
            return title;
        }

        private List<String> tags() {
            return tags;
        }
    }

    private static final class DocumentBuilder implements Builder<Document>, Thawed<Document> {
        private String title = "untitled";
        private final List<String> tags = new ArrayList<>();

        private DocumentBuilder title(String title) {
            this.title = title;
            return this;
        }

        private DocumentBuilder addTag(String tag) {
            tags.add(tag);
            return this;
        }

        private DocumentBuilder addTags(List<String> newTags) {
            tags.addAll(newTags);
            return this;
        }

        @Override
        public Document build() {
            return freeze();
        }

        @Override
        public Document freeze() {
            return new Document(title, tags);
        }
    }

    private interface ProfileEditor extends Thawed<Profile> {
        ProfileEditor rename(String name);

        ProfileEditor allow(String permission);
    }

    private static final class Profile implements Frozen<ProfileEditor> {
        private final String name;
        private final List<String> permissions;

        private Profile(String name, List<String> permissions) {
            this.name = name;
            this.permissions = List.copyOf(permissions);
        }

        @Override
        public ProfileEditor thaw() {
            return new MutableProfile(name, permissions);
        }

        private String name() {
            return name;
        }

        private List<String> permissions() {
            return permissions;
        }
    }

    private static final class MutableProfile implements ProfileEditor {
        private String name;
        private final List<String> permissions;

        private MutableProfile(String name, List<String> permissions) {
            this.name = name;
            this.permissions = new ArrayList<>(permissions);
        }

        @Override
        public ProfileEditor rename(String name) {
            this.name = name;
            return this;
        }

        @Override
        public ProfileEditor allow(String permission) {
            permissions.add(permission);
            return this;
        }

        @Override
        public Profile freeze() {
            return new Profile(name, permissions);
        }
    }

    private static final class SearchQuery {
        private final String term;
        private final List<String> fields;

        private SearchQuery(String term, List<String> fields) {
            this.term = term;
            this.fields = List.copyOf(fields);
        }

        private String term() {
            return term;
        }

        private List<String> fields() {
            return fields;
        }
    }

    private static final class SearchQueryBuilder implements Builder<SearchQuery> {
        private String term = "";
        private final List<String> fields = new ArrayList<>();

        private SearchQueryBuilder term(String term) {
            this.term = term;
            return this;
        }

        private SearchQueryBuilder includeField(String field) {
            fields.add(field);
            return this;
        }

        @Override
        public SearchQuery build() {
            return new SearchQuery(term, fields);
        }
    }
}
