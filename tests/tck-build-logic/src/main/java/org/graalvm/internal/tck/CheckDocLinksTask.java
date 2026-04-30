/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies that every repo-local link in Markdown documentation resolves to
 * an existing path inside the repository.
 *
 * <p>Walks the repository for {@code .md} files (excluding generated/build/test
 * scratch directories), parses inline {@code [text](target)} and reference-style
 * {@code [label]: target} links, and fails the build if any link points to a
 * missing repository path. External URLs ({@code http://}, {@code https://},
 * {@code mailto:}, etc.) are intentionally skipped — only repository-local
 * targets are checked.
 */
public abstract class CheckDocLinksTask extends DefaultTask {

    private static final Set<String> EXCLUDED_DIRECTORIES = new HashSet<>(Arrays.asList(
            ".git", ".gradle", ".idea", ".venv", "build", "node_modules",
            "metadata", "tests", "local_repositories", "native-build-tools"
    ));

    private static final List<String> EXTERNAL_PROTOCOLS = Arrays.asList(
            "http://", "https://", "mailto:", "ftp://", "ftps://",
            "ssh://", "git://", "tel:"
    );

    private static final Pattern INLINE_LINK = Pattern.compile(
            "!?" +                                  // optional image marker
            "\\[" +
            "(?:\\\\.|[^\\[\\]\\\\])*" +            // link text
            "]\\(\\s*" +
            "(?<target>(?:\\\\.|[^\\s)\\\\])+)" +   // link target
            "(?:\\s+\"[^\"]*\")?" +                 // optional title
            "(?:\\s+'[^']*')?" +
            "\\s*\\)"
    );

    private static final Pattern REFERENCE_LINK = Pattern.compile(
            "(?m)^\\s{0,3}\\[(?:\\\\.|[^\\[\\]\\\\])+]:\\s+(?<target>\\S+)"
    );

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```|~~~)");

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRepoRoot();

    @TaskAction
    public void check() throws IOException {
        Path repoRoot = getRepoRoot().get().getAsFile().toPath().toAbsolutePath().normalize();
        List<String> errors = new ArrayList<>();
        List<Path> markdownFiles = collectMarkdownFiles(repoRoot);
        for (Path markdownFile : markdownFiles) {
            String content = stripCodeFences(Files.readString(markdownFile));
            checkLinksInContent(repoRoot, markdownFile, content, INLINE_LINK, errors);
            checkLinksInContent(repoRoot, markdownFile, content, REFERENCE_LINK, errors);
        }
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Broken repo-local Markdown links found:\n");
            for (String error : errors) {
                message.append("  ").append(error).append('\n');
            }
            message.append("\nTotal broken links: ").append(errors.size());
            throw new GradleException(message.toString());
        }
        getLogger().lifecycle("All repo-local Markdown links resolve.");
    }

    private List<Path> collectMarkdownFiles(Path repoRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(repoRoot) && EXCLUDED_DIRECTORIES.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".md")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Path::compareTo);
        return files;
    }

    private static String stripCodeFences(String content) {
        StringBuilder out = new StringBuilder(content.length());
        boolean inFence = false;
        for (String line : content.split("\\R", -1)) {
            if (CODE_FENCE.matcher(line).find()) {
                inFence = !inFence;
                out.append('\n');
                continue;
            }
            out.append(inFence ? "" : line).append('\n');
        }
        return out.toString();
    }

    private void checkLinksInContent(
            Path repoRoot, Path markdownFile, String content, Pattern pattern, List<String> errors
    ) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String target = matcher.group("target").trim();
            if (target.startsWith("<") && target.endsWith(">")) {
                target = target.substring(1, target.length() - 1);
            }
            if (target.isEmpty() || isExternal(target) || target.startsWith("#")) {
                continue;
            }
            String pathPart = target;
            int hash = pathPart.indexOf('#');
            if (hash >= 0) {
                pathPart = pathPart.substring(0, hash);
            }
            if (pathPart.isEmpty()) {
                continue;
            }
            Path resolved;
            if (pathPart.startsWith("/")) {
                resolved = repoRoot.resolve(pathPart.substring(1)).normalize();
            } else {
                resolved = markdownFile.getParent().resolve(pathPart).normalize();
            }
            if (!Files.exists(resolved)) {
                int line = lineNumberOf(content, matcher.start());
                errors.add(String.format(
                        "%s:%d: target not found: %s -> %s",
                        repoRoot.relativize(markdownFile),
                        line,
                        target,
                        repoRoot.relativize(resolved)
                ));
            }
        }
    }

    private static boolean isExternal(String target) {
        String lower = target.toLowerCase();
        for (String prefix : EXTERNAL_PROTOCOLS) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static int lineNumberOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
