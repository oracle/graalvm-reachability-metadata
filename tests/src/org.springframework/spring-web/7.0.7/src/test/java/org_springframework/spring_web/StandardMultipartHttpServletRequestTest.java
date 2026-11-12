/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardMultipartHttpServletRequestTest {

    @TempDir
    Path directory;

    @Test
    void bindsServletPartToUploadFormAndTransfersItsContents() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/uploads");
        request.setContentType("multipart/form-data; boundary=upload-boundary");
        MockPart part = new MockPart("document", "notes.txt", "upload contents".getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        request.addPart(part);
        StandardMultipartHttpServletRequest multipart = new StandardMultipartHttpServletRequest(request);
        UploadForm form = new UploadForm();
        ServletRequestDataBinder binder = new ServletRequestDataBinder(form);
        binder.bind(multipart);

        assertThat(binder.getBindingResult().hasErrors()).isFalse();
        MultipartFile document = form.getDocument();
        assertThat(document.getOriginalFilename()).isEqualTo("notes.txt");
        assertThat(document.getContentType()).isEqualTo("text/plain");
        assertThat(document.getBytes()).isEqualTo("upload contents".getBytes(StandardCharsets.UTF_8));
        Path destination = directory.resolve("saved.txt");
        document.transferTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("upload contents");
    }

    public static final class UploadForm {
        private MultipartFile document;

        public MultipartFile getDocument() {
            return this.document;
        }

        public void setDocument(MultipartFile document) {
            this.document = document;
        }
    }
}
