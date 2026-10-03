/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ai_djl_huggingface.tokenizers;

import static org.assertj.core.api.Assertions.assertThat;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TokenizersTest {

    private static final String TOKENIZER_JSON = """
            {
              "version": "1.0",
              "truncation": null,
              "padding": null,
              "added_tokens": [
                {"id": 0, "content": "[PAD]", "single_word": false, "lstrip": false,
                 "rstrip": false, "normalized": false, "special": true},
                {"id": 1, "content": "[UNK]", "single_word": false, "lstrip": false,
                 "rstrip": false, "normalized": false, "special": true},
                {"id": 2, "content": "[CLS]", "single_word": false, "lstrip": false,
                 "rstrip": false, "normalized": false, "special": true},
                {"id": 3, "content": "[SEP]", "single_word": false, "lstrip": false,
                 "rstrip": false, "normalized": false, "special": true}
              ],
              "normalizer": {
                "type": "BertNormalizer",
                "clean_text": true,
                "handle_chinese_chars": true,
                "strip_accents": null,
                "lowercase": true
              },
              "pre_tokenizer": {"type": "BertPreTokenizer"},
              "post_processor": {
                "type": "TemplateProcessing",
                "single": [
                  {"SpecialToken": {"id": "[CLS]", "type_id": 0}},
                  {"Sequence": {"id": "A", "type_id": 0}},
                  {"SpecialToken": {"id": "[SEP]", "type_id": 0}}
                ],
                "pair": [
                  {"SpecialToken": {"id": "[CLS]", "type_id": 0}},
                  {"Sequence": {"id": "A", "type_id": 0}},
                  {"SpecialToken": {"id": "[SEP]", "type_id": 0}},
                  {"Sequence": {"id": "B", "type_id": 1}},
                  {"SpecialToken": {"id": "[SEP]", "type_id": 1}}
                ],
                "special_tokens": {
                  "[CLS]": {"id": "[CLS]", "ids": [2], "tokens": ["[CLS]"]},
                  "[SEP]": {"id": "[SEP]", "ids": [3], "tokens": ["[SEP]"]}
                }
              },
              "decoder": {"type": "WordPiece", "prefix": "##", "cleanup": true},
              "model": {
                "type": "WordPiece",
                "unk_token": "[UNK]",
                "continuing_subword_prefix": "##",
                "max_input_chars_per_word": 100,
                "vocab": {
                  "[PAD]": 0,
                  "[UNK]": 1,
                  "[CLS]": 2,
                  "[SEP]": 3,
                  "hello": 4,
                  "world": 5,
                  "!": 6
                }
              }
            }
            """;

    @Test
    void encodesAndDecodesTextWithAPathLoadedTokenizer() throws Exception {
        Path tokenizerDirectory = Files.createTempDirectory("djl-tokenizer");
        Path tokenizerPath = tokenizerDirectory.resolve("tokenizer.json");
        Files.writeString(tokenizerPath, TOKENIZER_JSON);

        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        try {
            Encoding encoding = tokenizer.encode("Hello world!");

            assertThat(encoding.getTokens()).containsExactly("[CLS]", "hello", "world", "!", "[SEP]");
            assertThat(encoding.getIds()).containsExactly(2L, 4L, 5L, 6L, 3L);
            assertThat(encoding.getAttentionMask()).containsExactly(1L, 1L, 1L, 1L, 1L);
            assertThat(encoding.getSpecialTokenMask()).containsExactly(1L, 0L, 0L, 0L, 1L);
            assertThat(tokenizer.decode(encoding.getIds(), true)).isEqualTo("hello world!");

            assertThat(encoding.getCharTokenSpans()).hasSize(5);
            assertThat(encoding.getCharTokenSpans()[1].getStart()).isEqualTo(0);
            assertThat(encoding.getCharTokenSpans()[1].getEnd()).isEqualTo(5);
            assertThat(encoding.getCharTokenSpans()[2].getStart()).isEqualTo(6);
            assertThat(encoding.getCharTokenSpans()[2].getEnd()).isEqualTo(11);
        } finally {
            tokenizer.close();
            Files.deleteIfExists(tokenizerPath);
            Files.deleteIfExists(tokenizerDirectory);
        }
    }

    @Test
    void handlesPairAndBatchEncodingThroughThePublicApi() throws Exception {
        Path tokenizerDirectory = Files.createTempDirectory("djl-tokenizer");
        Path tokenizerPath = tokenizerDirectory.resolve("tokenizer.json");
        Files.writeString(tokenizerPath, TOKENIZER_JSON);

        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        try {
            Encoding pair = tokenizer.encode("hello", "world");
            assertThat(pair.getTokens()).containsExactly("[CLS]", "hello", "[SEP]", "world", "[SEP]");
            assertThat(pair.getTypeIds()).containsExactly(0L, 0L, 0L, 1L, 1L);
            assertThat(pair.getSequenceIds()).hasSize(5);

            Encoding[] batch = tokenizer.batchEncode(List.of("hello", "world"));
            assertThat(batch).hasSize(2);
            assertThat(batch[0].getTokens()).containsExactly("[CLS]", "hello", "[SEP]");
            assertThat(batch[1].getTokens()).containsExactly("[CLS]", "world", "[SEP]");
            assertThat(tokenizer.batchDecode(new long[][] {batch[0].getIds(), batch[1].getIds()}, true))
                    .containsExactly("hello", "world");
        } finally {
            tokenizer.close();
            Files.deleteIfExists(tokenizerPath);
            Files.deleteIfExists(tokenizerDirectory);
        }
    }

    @Test
    void appliesTruncationConfiguredWithTheBuilder() throws Exception {
        Path tokenizerDirectory = Files.createTempDirectory("djl-tokenizer");
        Path tokenizerPath = tokenizerDirectory.resolve("tokenizer.json");
        Files.writeString(tokenizerPath, TOKENIZER_JSON);

        HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(tokenizerPath)
                .optAddSpecialTokens(false)
                .optTruncation(true)
                .optMaxLength(3)
                .build();
        try {
            Encoding encoding = tokenizer.encode("hello world ! hello");

            assertThat(tokenizer.getMaxLength()).isEqualTo(3);
            assertThat(encoding.getTokens()).containsExactly("hello", "world", "!");
            assertThat(encoding.getIds()).containsExactly(4L, 5L, 6L);
            assertThat(encoding.exceedMaxLength()).isTrue();
        } finally {
            tokenizer.close();
            Files.deleteIfExists(tokenizerPath);
            Files.deleteIfExists(tokenizerDirectory);
        }
    }
}

