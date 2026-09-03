/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_veracode_annotation.VeracodeAnnotations;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Path;

import com.veracode.annotation.CRLFCleanser;
import com.veracode.annotation.FilePathCleanser;
import com.veracode.annotation.RedirectURLCleanser;
import com.veracode.annotation.SQLQueryCleanser;
import com.veracode.annotation.XSSCleanser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VeracodeAnnotationsTest {
    @Test
    void crlfCleanserSupportsDefaultAndConfiguredMethodAnnotations() {
        ApplicationCleansers cleansers = new ApplicationCleansers();
        CRLFCleanser annotation = new CRLFCleanserLiteral("header-value", "Removes line breaks from headers");

        assertThat(cleansers.removeLineBreaks("trusted\r\nvalue")).isEqualTo("trustedvalue");
        assertThat(cleansers.normalizeHeader("  trusted\nvalue  ")).isEqualTo("trustedvalue");
        assertThat(annotation.value()).isEqualTo("header-value");
        assertThat(annotation.userComment()).isEqualTo("Removes line breaks from headers");
        assertThat(annotation.annotationType()).isSameAs(CRLFCleanser.class);
    }

    @Test
    void filePathCleanserSupportsDefaultAndConfiguredMethodAnnotations() {
        ApplicationCleansers cleansers = new ApplicationCleansers();
        FilePathCleanser annotation = new FilePathCleanserLiteral("leaf-name", "Restricts files to an export root");
        Path exportRoot = Path.of("safe", "exports");

        assertThat(cleansers.leafName(Path.of("incoming", "daily", "report.csv"))).isEqualTo("report.csv");
        assertThat(cleansers.resolveInExportRoot(exportRoot, Path.of("..", "private", "report.csv")))
                .isEqualTo(exportRoot.resolve("report.csv"));
        assertThat(annotation.value()).isEqualTo("leaf-name");
        assertThat(annotation.userComment()).isEqualTo("Restricts files to an export root");
        assertThat(annotation.annotationType()).isSameAs(FilePathCleanser.class);
    }

    @Test
    void redirectUrlCleanserSupportsDefaultAndConfiguredMethodAnnotations() {
        ApplicationCleansers cleansers = new ApplicationCleansers();
        RedirectURLCleanser annotation =
                new RedirectURLCleanserLiteral("application-route", "Allows known local redirect targets");

        assertThat(cleansers.allowlistedRoute("/profile")).isEqualTo("/profile");
        assertThat(cleansers.allowlistedRoute("https://untrusted.example/landing")).isEqualTo("/");
        assertThat(cleansers.applicationRedirect("/home"))
                .isEqualTo(URI.create("https://application.example/home"));
        assertThat(annotation.value()).isEqualTo("application-route");
        assertThat(annotation.userComment()).isEqualTo("Allows known local redirect targets");
        assertThat(annotation.annotationType()).isSameAs(RedirectURLCleanser.class);
    }

    @Test
    void sqlQueryCleanserSupportsDefaultAndConfiguredMethodAnnotations() {
        ApplicationCleansers cleansers = new ApplicationCleansers();
        SQLQueryCleanser annotation = new SQLQueryCleanserLiteral("sort-column", "Uses an allowlisted ORDER BY column");

        assertThat(cleansers.allowlistedSortColumn("created_at")).isEqualTo("created_at");
        assertThat(cleansers.allowlistedSortColumn("name DESC; DROP TABLE users")).isEqualTo("name");
        assertThat(cleansers.orderByClause("created_at")).isEqualTo("ORDER BY created_at");
        assertThat(annotation.value()).isEqualTo("sort-column");
        assertThat(annotation.userComment()).isEqualTo("Uses an allowlisted ORDER BY column");
        assertThat(annotation.annotationType()).isSameAs(SQLQueryCleanser.class);
    }

    @Test
    void xssCleanserSupportsDefaultAndConfiguredMethodAnnotations() {
        ApplicationCleansers cleansers = new ApplicationCleansers();
        XSSCleanser annotation = new XSSCleanserLiteral("html-text", "Escapes user-visible text");

        assertThat(cleansers.escapeHtml("<strong>Tom & Jerry</strong>"))
                .isEqualTo("&lt;strong&gt;Tom &amp; Jerry&lt;/strong&gt;");
        assertThat(cleansers.escapeHtmlAttribute("\"quoted\" & 'single'"))
                .isEqualTo("&quot;quoted&quot; &amp; &#39;single&#39;");
        assertThat(annotation.value()).isEqualTo("html-text");
        assertThat(annotation.userComment()).isEqualTo("Escapes user-visible text");
        assertThat(annotation.annotationType()).isSameAs(XSSCleanser.class);
    }

    private static final class ApplicationCleansers {
        @CRLFCleanser
        private String removeLineBreaks(String value) {
            return value.replace("\r", "").replace("\n", "");
        }

        @CRLFCleanser(value = "header-value", userComment = "Removes line breaks from headers")
        private String normalizeHeader(String value) {
            return removeLineBreaks(value).trim();
        }

        @FilePathCleanser
        private String leafName(Path path) {
            return path.getFileName().toString();
        }

        @FilePathCleanser(value = "leaf-name", userComment = "Restricts files to an export root")
        private Path resolveInExportRoot(Path exportRoot, Path requestedPath) {
            return exportRoot.resolve(leafName(requestedPath));
        }

        @RedirectURLCleanser
        private String allowlistedRoute(String requestedRoute) {
            return switch (requestedRoute) {
                case "/home", "/profile" -> requestedRoute;
                default -> "/";
            };
        }

        @RedirectURLCleanser(value = "application-route", userComment = "Allows known local redirect targets")
        private URI applicationRedirect(String requestedRoute) {
            return URI.create("https://application.example").resolve(allowlistedRoute(requestedRoute));
        }

        @SQLQueryCleanser
        private String allowlistedSortColumn(String requestedColumn) {
            return switch (requestedColumn) {
                case "name", "created_at" -> requestedColumn;
                default -> "name";
            };
        }

        @SQLQueryCleanser(value = "sort-column", userComment = "Uses an allowlisted ORDER BY column")
        private String orderByClause(String requestedColumn) {
            return "ORDER BY " + allowlistedSortColumn(requestedColumn);
        }

        @XSSCleanser
        private String escapeHtml(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        @XSSCleanser(value = "html-text", userComment = "Escapes user-visible text")
        private String escapeHtmlAttribute(String value) {
            return escapeHtml(value).replace("\"", "&quot;").replace("'", "&#39;");
        }
    }

    private abstract static class CleanserAnnotationLiteral implements Annotation {
        private final String value;
        private final String userComment;

        private CleanserAnnotationLiteral(String value, String userComment) {
            this.value = value;
            this.userComment = userComment;
        }

        public String value() {
            return value;
        }

        public String userComment() {
            return userComment;
        }
    }

    private static final class CRLFCleanserLiteral extends CleanserAnnotationLiteral implements CRLFCleanser {
        private CRLFCleanserLiteral(String value, String userComment) {
            super(value, userComment);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return CRLFCleanser.class;
        }
    }

    private static final class FilePathCleanserLiteral extends CleanserAnnotationLiteral implements FilePathCleanser {
        private FilePathCleanserLiteral(String value, String userComment) {
            super(value, userComment);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return FilePathCleanser.class;
        }
    }

    private static final class RedirectURLCleanserLiteral extends CleanserAnnotationLiteral
            implements RedirectURLCleanser {
        private RedirectURLCleanserLiteral(String value, String userComment) {
            super(value, userComment);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return RedirectURLCleanser.class;
        }
    }

    private static final class SQLQueryCleanserLiteral extends CleanserAnnotationLiteral implements SQLQueryCleanser {
        private SQLQueryCleanserLiteral(String value, String userComment) {
            super(value, userComment);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return SQLQueryCleanser.class;
        }
    }

    private static final class XSSCleanserLiteral extends CleanserAnnotationLiteral implements XSSCleanser {
        private XSSCleanserLiteral(String value, String userComment) {
            super(value, userComment);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return XSSCleanser.class;
        }
    }
}
