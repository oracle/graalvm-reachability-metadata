/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's XML parser subsystem to read an Atom feed for a journal application.
 */
public class AtomFeedParsingTest {

    @Test
    @Timeout(60)
    public void readsEntriesAndResolvesTheirLinks() {
        String atomFeed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Trail Journal</title>
                  <entry id="post-7">
                    <title>Rain on the ridge</title>
                    <link href="/posts/7" />
                    <summary>Pack light &amp; stay dry.</summary>
                    <category term="hiking" />
                  </entry>
                  <entry id="post-8">
                    <title>Camp cooking</title>
                    <link href="/posts/8" />
                  </entry>
                </feed>
                """;

        Document feed = Jsoup.parse(
                atomFeed,
                "https://journal.example.test/feed.xml",
                Parser.xmlParser());
        List<FeedEntry> entries = feed.getElementsByTag("entry").stream()
                .map(AtomFeedParsingTest::readEntry)
                .collect(Collectors.toList());

        assertEquals("http://www.w3.org/2005/Atom", feed.child(0).tag().namespace());
        assertEquals("Trail Journal", feed.getElementsByTag("title").first().text());
        assertEquals(List.of(
                new FeedEntry("post-7", "Rain on the ridge",
                        "https://journal.example.test/posts/7", "hiking",
                        "Pack light & stay dry."),
                new FeedEntry("post-8", "Camp cooking",
                        "https://journal.example.test/posts/8", "", "")), entries);
        assertTrue(feed.getElementsByTag("entry").get(1)
                .getElementsByTag("summary").isEmpty());
    }

    private static FeedEntry readEntry(Element entry) {
        Element link = entry.getElementsByTag("link").first();
        Element category = entry.getElementsByTag("category").first();
        Element summary = entry.getElementsByTag("summary").first();
        return new FeedEntry(
                entry.attr("id"),
                entry.getElementsByTag("title").first().text(),
                link.absUrl("href"),
                category == null ? "" : category.attr("term"),
                summary == null ? "" : summary.text());
    }

    private record FeedEntry(
            String id,
            String title,
            String link,
            String category,
            String summary) {
    }
}
