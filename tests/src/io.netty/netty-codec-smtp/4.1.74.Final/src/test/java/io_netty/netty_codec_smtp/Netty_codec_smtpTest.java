/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.smtp.DefaultLastSmtpContent;
import io.netty.handler.codec.smtp.DefaultSmtpContent;
import io.netty.handler.codec.smtp.DefaultSmtpRequest;
import io.netty.handler.codec.smtp.DefaultSmtpResponse;
import io.netty.handler.codec.smtp.LastSmtpContent;
import io.netty.handler.codec.smtp.SmtpCommand;
import io.netty.handler.codec.smtp.SmtpContent;
import io.netty.handler.codec.smtp.SmtpRequest;
import io.netty.handler.codec.smtp.SmtpRequestEncoder;
import io.netty.handler.codec.smtp.SmtpRequests;
import io.netty.handler.codec.smtp.SmtpResponse;
import io.netty.handler.codec.smtp.SmtpResponseDecoder;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

public class Netty_codec_smtpTest {
    @Test
    void smtpCommandValueOfReusesKnownCommandsAndKeepsCustomNames() {
        SmtpCommand canonicalMail = SmtpCommand.valueOf("MAIL");
        SmtpCommand lowerCaseMail = SmtpCommand.valueOf("mail");
        SmtpCommand customCommand = SmtpCommand.valueOf("XCLIENT");

        assertThat(canonicalMail).isSameAs(SmtpCommand.MAIL);
        assertThat(lowerCaseMail).isEqualTo(SmtpCommand.MAIL);
        assertThat(lowerCaseMail.name().toString()).isEqualTo("mail");
        assertThat(customCommand.name().toString()).isEqualTo("XCLIENT");
        assertThat(customCommand).isEqualTo(SmtpCommand.valueOf("xclient"));
        assertThat(customCommand.toString()).contains("XCLIENT");
    }

    @Test
    void requestFactoryCreatesProtocolSpecificCommandsAndImmutableParameters() {
        SmtpRequest ehlo = SmtpRequests.ehlo("client.example.org");
        SmtpRequest mail = SmtpRequests.mail("sender@example.org", "BODY=8BITMIME", "SMTPUTF8");
        SmtpRequest nullReversePath = SmtpRequests.mail(null);
        SmtpRequest recipient = SmtpRequests.rcpt("recipient@example.org", "NOTIFY=SUCCESS,FAILURE");
        SmtpRequest auth = SmtpRequests.auth("PLAIN", "AHVzZXIAc2VjcmV0");
        SmtpRequest empty = SmtpRequests.empty("comment", "ignored-by-server");

        assertThat(ehlo.command()).isSameAs(SmtpCommand.EHLO);
        assertThat(ehlo.parameters()).containsExactly("client.example.org");
        assertThat(mail.command()).isSameAs(SmtpCommand.MAIL);
        assertThat(mail.parameters()).containsExactly("FROM:<sender@example.org>", "BODY=8BITMIME", "SMTPUTF8");
        assertThat(nullReversePath.parameters()).extracting(CharSequence::toString).containsExactly("FROM:<>");
        assertThat(recipient.command()).isSameAs(SmtpCommand.RCPT);
        assertThat(recipient.parameters()).containsExactly("TO:<recipient@example.org>", "NOTIFY=SUCCESS,FAILURE");
        assertThat(auth.command()).isSameAs(SmtpCommand.AUTH);
        assertThat(auth.parameters()).containsExactly("PLAIN", "AHVzZXIAc2VjcmV0");
        assertThat(empty.command()).isSameAs(SmtpCommand.EMPTY);
        assertThat(empty.parameters()).containsExactly("comment", "ignored-by-server");
        assertThat(SmtpRequests.noop()).isSameAs(SmtpRequests.noop());
        assertThat(SmtpRequests.data()).isSameAs(SmtpRequests.data());
        assertThat(SmtpRequests.help(null)).isSameAs(SmtpRequests.help(null));

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> mail.parameters().add("SIZE=123"));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> SmtpRequests.rcpt(null));
    }

    @Test
    void defaultMessagesValidateCodesAndImplementValueSemantics() {
        DefaultSmtpRequest request = new DefaultSmtpRequest("xforward", "NAME=client", "ADDR=127.0.0.1");
        DefaultSmtpRequest equalRequest = new DefaultSmtpRequest("xforward", "NAME=client", "ADDR=127.0.0.1");
        DefaultSmtpResponse response = new DefaultSmtpResponse(250, "PIPELINING", "SIZE 4096");
        DefaultSmtpResponse equalResponse = new DefaultSmtpResponse(250, "PIPELINING", "SIZE 4096");

        assertThat(request.command()).isEqualTo(SmtpCommand.valueOf("XFORWARD"));
        assertThat(request.command().name().toString()).isEqualTo("xforward");
        assertThat(request.parameters()).containsExactly("NAME=client", "ADDR=127.0.0.1");
        assertThat(request).isEqualTo(equalRequest);
        assertThat(request.hashCode()).isEqualTo(equalRequest.hashCode());
        assertThat(request.toString()).contains("xforward", "NAME=client");
        assertThat(response.code()).isEqualTo(250);
        assertThat(response.details()).containsExactly("PIPELINING", "SIZE 4096");
        assertThat(response).isEqualTo(equalResponse);
        assertThat(response.hashCode()).isEqualTo(equalResponse.hashCode());
        assertThat(response.toString()).contains("250", "PIPELINING");

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> response.details().add("STARTTLS"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DefaultSmtpResponse(99));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new DefaultSmtpResponse(600));
    }

    @Test
    void requestEncoderWritesCommandsWithArgumentsAndEmptyCommandLines() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpRequestEncoder());
        try {
            assertThat(channel.writeOutbound(SmtpRequests.ehlo("client.example.org"))).isTrue();
            assertThat(channel.writeOutbound(SmtpRequests.mail(
                    "sender@example.org", "BODY=8BITMIME", "SMTPUTF8"))).isTrue();
            assertThat(channel.writeOutbound(SmtpRequests.rcpt(
                    "recipient@example.org", "NOTIFY=SUCCESS,FAILURE"))).isTrue();
            assertThat(channel.writeOutbound(SmtpRequests.auth("PLAIN", "AHVzZXIAc2VjcmV0"))).isTrue();
            assertThat(channel.writeOutbound(new DefaultSmtpRequest(
                    "XCLIENT", "ADDR=192.0.2.10", "NAME=mail.example.org"))).isTrue();
            assertThat(channel.writeOutbound(SmtpRequests.empty("raw server extension"))).isTrue();
            assertThat(channel.writeOutbound(SmtpRequests.quit())).isTrue();

            assertThat(readOutboundText(channel)).isEqualTo("EHLO client.example.org\r\n");
            assertThat(readOutboundText(channel))
                    .isEqualTo("MAIL FROM:<sender@example.org> BODY=8BITMIME SMTPUTF8\r\n");
            assertThat(readOutboundText(channel))
                    .isEqualTo("RCPT TO:<recipient@example.org> NOTIFY=SUCCESS,FAILURE\r\n");
            assertThat(readOutboundText(channel)).isEqualTo("AUTH PLAIN AHVzZXIAc2VjcmV0\r\n");
            assertThat(readOutboundText(channel)).isEqualTo("XCLIENT ADDR=192.0.2.10 NAME=mail.example.org\r\n");
            assertThat(readOutboundText(channel)).isEqualTo("raw server extension\r\n");
            assertThat(readOutboundText(channel)).isEqualTo("QUIT\r\n");
            assertThat(channel.<ByteBuf>readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void requestEncoderStreamsDataContentAndTerminatesLastContent() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpRequestEncoder());
        try {
            SmtpContent firstChunk = new DefaultSmtpContent(copiedBuffer("Subject: test\r\n\r\nHello "));
            LastSmtpContent lastChunk = new DefaultLastSmtpContent(copiedBuffer("world\r\n"));

            assertThat(channel.writeOutbound(SmtpRequests.data())).isTrue();
            assertThat(channel.writeOutbound(firstChunk)).isTrue();
            assertThat(channel.writeOutbound(lastChunk)).isTrue();

            assertThat(readOutboundText(channel)).isEqualTo("DATA\r\n");
            assertThat(readOutboundText(channel)).isEqualTo("Subject: test\r\n\r\nHello ");
            assertThat(readOutboundText(channel)).isEqualTo("world\r\n");
            assertThat(readOutboundText(channel)).isEqualTo(".\r\n");
            assertThat(channel.<ByteBuf>readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void requestEncoderEnforcesDataStateAndAllowsRsetToAbortMailBody() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpRequestEncoder());
        try {
            assertThatThrownBy(() -> channel.writeOutbound(new DefaultSmtpContent(copiedBuffer("orphan"))))
                    .satisfies(Netty_codec_smtpTest::assertNoContentExpectedFailure);

            assertThat(channel.writeOutbound(SmtpRequests.data())).isTrue();
            assertThat(readOutboundText(channel)).isEqualTo("DATA\r\n");
            assertThatThrownBy(() -> channel.writeOutbound(SmtpRequests.mail("sender@example.org")))
                    .satisfies(Netty_codec_smtpTest::assertContentExpectedFailure);

            assertThat(channel.writeOutbound(SmtpRequests.rset())).isTrue();
            assertThat(channel.writeOutbound(SmtpRequests.mail("sender@example.org"))).isTrue();
            assertThat(readOutboundText(channel)).isEqualTo("RSET\r\n");
            assertThat(readOutboundText(channel)).isEqualTo("MAIL FROM:<sender@example.org>\r\n");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void responseDecoderParsesSingleLineAndMultiLineResponses() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpResponseDecoder(128));
        try {
            assertThat(channel.writeInbound(copiedBuffer("220 smtp.example.org ESMTP ready\r\n"))).isTrue();
            SmtpResponse greeting = channel.readInbound();
            assertThat(greeting.code()).isEqualTo(220);
            assertThat(greeting.details()).containsExactly("smtp.example.org ESMTP ready");
            assertThat(channel.<SmtpResponse>readInbound()).isNull();

            String capabilitiesLines = "250-mail.example.org\r\n"
                    + "250-PIPELINING\r\n"
                    + "250-SIZE 4096\r\n"
                    + "250 SMTPUTF8\r\n";
            assertThat(channel.writeInbound(copiedBuffer(capabilitiesLines))).isTrue();
            SmtpResponse capabilities = channel.readInbound();
            assertThat(capabilities.code()).isEqualTo(250);
            assertThat(capabilities.details())
                    .containsExactly("mail.example.org", "PIPELINING", "SIZE 4096", "SMTPUTF8");
            assertThat(channel.<SmtpResponse>readInbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void responseDecoderAccumulatesMultiLineResponsesAcrossInboundWrites() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpResponseDecoder(128));
        try {
            assertThat(channel.writeInbound(copiedBuffer("250-mail.example.org\r\n"))).isFalse();
            assertThat(channel.<SmtpResponse>readInbound()).isNull();
            assertThat(channel.writeInbound(copiedBuffer("250-ENHANCEDSTATUSCODES\r\n"))).isFalse();
            assertThat(channel.<SmtpResponse>readInbound()).isNull();
            assertThat(channel.writeInbound(copiedBuffer("250 HELP\r\n"))).isTrue();

            SmtpResponse response = channel.readInbound();
            assertThat(response.code()).isEqualTo(250);
            assertThat(response.details()).containsExactly("mail.example.org", "ENHANCEDSTATUSCODES", "HELP");
            assertThat(channel.<SmtpResponse>readInbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void responseDecoderWaitsForCompleteLinesAndRejectsMalformedLines() {
        EmbeddedChannel partialChannel = new EmbeddedChannel(new SmtpResponseDecoder(128));
        try {
            assertThat(partialChannel.writeInbound(copiedBuffer("354 Start mail input"))).isFalse();
            assertThat(partialChannel.<SmtpResponse>readInbound()).isNull();
            assertThat(partialChannel.writeInbound(copiedBuffer("\r\n"))).isTrue();
            SmtpResponse response = partialChannel.readInbound();
            assertThat(response.code()).isEqualTo(354);
            assertThat(response.details()).containsExactly("Start mail input");
        } finally {
            partialChannel.finishAndReleaseAll();
        }

        EmbeddedChannel invalidChannel = new EmbeddedChannel(new SmtpResponseDecoder(128));
        try {
            assertThatThrownBy(() -> invalidChannel.writeInbound(copiedBuffer("OK not an SMTP response\r\n")))
                    .isInstanceOf(DecoderException.class)
                    .hasMessageContaining("Received invalid line");
        } finally {
            invalidChannel.finishAndReleaseAll();
        }
    }

    @Test
    void responseDecoderRejectsLinesLongerThanConfiguredMaximum() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpResponseDecoder(10));
        try {
            assertThatThrownBy(() -> channel.writeInbound(copiedBuffer("250 response text exceeds limit\r\n")))
                    .isInstanceOf(TooLongFrameException.class)
                    .hasMessageContaining("frame length");
            assertThat(channel.<SmtpResponse>readInbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void smtpContentPreservesConcreteTypesAcrossCopyDuplicateAndReplace() {
        DefaultSmtpContent content = new DefaultSmtpContent(copiedBuffer("payload"));
        DefaultLastSmtpContent lastContent = new DefaultLastSmtpContent(copiedBuffer("final"));
        SmtpContent copied = null;
        SmtpContent duplicate = null;
        SmtpContent retainedDuplicate = null;
        SmtpContent replaced = null;
        LastSmtpContent lastCopy = null;
        LastSmtpContent lastDuplicate = null;
        LastSmtpContent lastReplacement = null;
        try {
            copied = content.copy();
            duplicate = content.duplicate();
            retainedDuplicate = content.retainedDuplicate();
            replaced = content.replace(copiedBuffer("replacement"));
            lastCopy = lastContent.copy();
            lastDuplicate = lastContent.retainedDuplicate();
            lastReplacement = lastContent.replace(copiedBuffer("new-final"));

            assertThat(copied).isInstanceOf(DefaultSmtpContent.class);
            assertThat(duplicate).isInstanceOf(DefaultSmtpContent.class);
            assertThat(retainedDuplicate).isInstanceOf(DefaultSmtpContent.class);
            assertThat(replaced).isInstanceOf(DefaultSmtpContent.class);
            assertThat(copied.content().toString(CharsetUtil.US_ASCII)).isEqualTo("payload");
            assertThat(duplicate.content().toString(CharsetUtil.US_ASCII)).isEqualTo("payload");
            assertThat(replaced.content().toString(CharsetUtil.US_ASCII)).isEqualTo("replacement");
            int referenceCount = content.refCnt();
            assertThat(content.retain().touch("hint")).isSameAs(content);
            assertThat(content.refCnt()).isEqualTo(referenceCount + 1);
            assertThat(content.release()).isFalse();
            assertThat(content.refCnt()).isEqualTo(referenceCount);

            assertThat(lastCopy).isInstanceOf(DefaultLastSmtpContent.class);
            assertThat(lastDuplicate).isInstanceOf(DefaultLastSmtpContent.class);
            assertThat(lastReplacement).isInstanceOf(DefaultLastSmtpContent.class);
            assertThat(lastCopy.content().toString(CharsetUtil.US_ASCII)).isEqualTo("final");
            assertThat(lastReplacement.content().toString(CharsetUtil.US_ASCII)).isEqualTo("new-final");
            assertThat(LastSmtpContent.EMPTY_LAST_CONTENT.content().readableBytes()).isZero();
        } finally {
            releaseIfAccessible(lastReplacement);
            releaseIfAccessible(lastDuplicate);
            releaseIfAccessible(lastCopy);
            releaseIfAccessible(replaced);
            releaseIfAccessible(retainedDuplicate);
            releaseIfAccessible(duplicate);
            releaseIfAccessible(copied);
            releaseIfAccessible(lastContent);
            releaseIfAccessible(content);
        }
    }

    private static ByteBuf copiedBuffer(String text) {
        return Unpooled.copiedBuffer(text, CharsetUtil.US_ASCII);
    }

    private static String readOutboundText(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        assertThat(buffer).isNotNull();
        try {
            return buffer.toString(CharsetUtil.US_ASCII);
        } finally {
            buffer.release();
        }
    }

    private static void assertNoContentExpectedFailure(Throwable throwable) {
        assertThat(rootCause(throwable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No SmtpContent expected");
    }

    private static void assertContentExpectedFailure(Throwable throwable) {
        assertThat(rootCause(throwable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SmtpContent expected");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (throwable instanceof EncoderException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return current;
    }

    private static void releaseIfAccessible(SmtpContent content) {
        if (content != null && content.refCnt() > 0) {
            content.release(content.refCnt());
        }
    }
}
