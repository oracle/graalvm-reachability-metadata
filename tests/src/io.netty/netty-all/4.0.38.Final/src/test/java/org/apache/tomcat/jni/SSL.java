/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tomcat.jni;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SSL {
    public static final int SSL_PROTOCOL_ALL = 0x3f;
    public static final int SSL_CVERIFY_NONE = 0;
    public static final int SSL_CVERIFY_OPTIONAL = 1;
    public static final int SSL_CVERIFY_REQUIRE = 2;
    public static final int SSL_VERIFY_NONE = 0;
    public static final int SSL_VERIFY_PEER = 1;
    public static final int SSL_AIDX_RSA = 0;
    public static final long SSL_SESS_CACHE_OFF = 0;
    public static final long SSL_SESS_CACHE_SERVER = 1;
    public static final int SSL_OP_ALL = 0x00000fff;
    public static final int SSL_OP_SINGLE_ECDH_USE = 1 << 12;
    public static final int SSL_OP_SINGLE_DH_USE = 1 << 13;
    public static final int SSL_OP_CIPHER_SERVER_PREFERENCE = 1 << 14;
    public static final int SSL_OP_NO_SSLv2 = 1 << 15;
    public static final int SSL_OP_NO_SSLv3 = 1 << 16;
    public static final int SSL_OP_NO_TLSv1 = 1 << 17;
    public static final int SSL_OP_NO_TLSv1_1 = 1 << 18;
    public static final int SSL_OP_NO_TLSv1_2 = 1 << 19;
    public static final int SSL_OP_NO_TICKET = 1 << 20;
    public static final int SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION = 1 << 21;
    public static final int SSL_MODE_CLIENT = 0;
    public static final int SSL_MODE_SERVER = 1;
    public static final int SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER = 1 << 1;
    public static final int SSL_SELECTOR_FAILURE_NO_ADVERTISE = 0;
    public static final int SSL_SELECTOR_FAILURE_CHOOSE_MY_LAST_PROTOCOL = 1;
    public static final int SSL_ST_CONNECT = 0x1000;
    public static final int SSL_ST_ACCEPT = 0x2000;
    public static final int SSL_SENT_SHUTDOWN = 1;
    public static final int SSL_RECEIVED_SHUTDOWN = 2;
    public static final int SSL_ERROR_NONE = 0;
    public static final int SSL_ERROR_SSL = 1;
    public static final int SSL_ERROR_WANT_READ = 2;
    public static final int SSL_ERROR_WANT_WRITE = 3;
    public static final int SSL_ERROR_WANT_X509_LOOKUP = 4;
    public static final int SSL_ERROR_SYSCALL = 5;
    public static final int SSL_ERROR_ZERO_RETURN = 6;
    public static final int SSL_ERROR_WANT_CONNECT = 7;
    public static final int SSL_ERROR_WANT_ACCEPT = 8;
    public static final int SSL_ERROR_NAME = 9;

    private static final AtomicLong IDS = new AtomicLong(2000);
    private static final ConcurrentMap<Long, Integer> OPTIONS = new ConcurrentHashMap<Long, Integer>();

    private SSL() {
    }

    public static int version() {
        return 0x10002000;
    }

    public static String versionString() {
        return "OpenSSL stub";
    }

    public static int initialize(String engine) {
        return 0;
    }

    public static String getLastError() {
        return "";
    }

    public static long newSSL(long context, boolean server) {
        long ssl = IDS.incrementAndGet();
        OPTIONS.put(ssl, SSLContext.getOptions(context));
        return ssl;
    }

    public static long newMemBIO() {
        return IDS.incrementAndGet();
    }

    public static int getError(long ssl, int result) {
        return result >= 0 ? SSL_ERROR_NONE : SSL_ERROR_SSL;
    }

    public static int pendingWrittenBytesInBIO(long bio) {
        return 0;
    }

    public static int pendingReadableBytesInSSL(long ssl) {
        return 0;
    }

    public static int writeToBIO(long bio, long bufferAddress, int length) {
        return length;
    }

    public static int readFromBIO(long bio, long bufferAddress, int length) {
        return 0;
    }

    public static boolean shouldRetryBIO(long bio) {
        return false;
    }

    public static int writeToSSL(long ssl, long bufferAddress, int length) {
        return length;
    }

    public static int readFromSSL(long ssl, long bufferAddress, int length) {
        return 0;
    }

    public static int getShutdown(long ssl) {
        return 0;
    }

    public static void setShutdown(long ssl, int mode) {
    }

    public static void freeSSL(long ssl) {
        OPTIONS.remove(ssl);
    }

    public static long makeNetworkBIO(long ssl) {
        return IDS.incrementAndGet();
    }

    public static long makeNetworkBIO(long ssl, int bufferSize) {
        return makeNetworkBIO(ssl);
    }

    public static long makeNetworkBIO(long ssl, int bufferSize, int applicationBufferSize) {
        return makeNetworkBIO(ssl);
    }

    public static void freeBIO(long bio) {
    }

    public static void flushBIO(long bio) {
    }

    public static int shutdownSSL(long ssl) {
        return 1;
    }

    public static int getLastErrorNumber() {
        return 0;
    }

    public static String getCipherForSSL(long ssl) {
        return "ECDHE-RSA-AES128-GCM-SHA256";
    }

    public static String getVersion(long ssl) {
        return "TLSv1.2";
    }

    public static int doHandshake(long ssl) {
        return 1;
    }

    public static int isInInit(long ssl) {
        return 0;
    }

    public static String getNextProtoNegotiated(long ssl) {
        return null;
    }

    public static String getAlpnSelected(long ssl) {
        return null;
    }

    public static byte[][] getPeerCertChain(long ssl) {
        return null;
    }

    public static byte[] getPeerCertificate(long ssl) {
        return null;
    }

    public static String getErrorString(long errorNumber) {
        return "";
    }

    public static long getTime(long ssl) {
        return System.currentTimeMillis() / 1000L;
    }

    public static long getTimeout(long ssl) {
        return 0;
    }

    public static long setTimeout(long ssl, long timeout) {
        return timeout;
    }

    public static void setVerify(long ssl, int level, int depth) {
    }

    public static void setOptions(long ssl, int options) {
        OPTIONS.put(ssl, getOptions(ssl) | options);
    }

    public static void clearOptions(long ssl, int options) {
        OPTIONS.put(ssl, getOptions(ssl) & ~options);
    }

    public static int getOptions(long ssl) {
        Integer options = OPTIONS.get(ssl);
        return options == null ? 0 : options;
    }

    public static String[] getCiphers(long ssl) {
        return new String[] {"ECDHE-RSA-AES128-GCM-SHA256"};
    }

    public static boolean setCipherSuites(long ssl, String cipherSuites) {
        return true;
    }

    public static byte[] getSessionId(long ssl) {
        return new byte[] {1, 2, 3};
    }

    public static int getHandshakeCount(long ssl) {
        return 1;
    }

    public static void clearError() {
    }

    public static int renegotiate(long ssl) {
        return 1;
    }

    public static void setState(long ssl, int state) {
    }

    public static void setTlsExtHostName(long ssl, String hostname) {
    }

    public static String[] authenticationMethods(long ssl) {
        return new String[] {"RSA"};
    }

    public static void setCertificateChainBio(long ssl, long certificateChainBio, boolean skipFirst) {
    }

    public static void setCertificateBio(long ssl, long certificateBio, long keyBio, String password) {
    }

    public static long parsePrivateKey(long privateKeyBio, String password) {
        return IDS.incrementAndGet();
    }

    public static void freePrivateKey(long privateKey) {
    }

    public static long parseX509Chain(long certificateChainBio) {
        return IDS.incrementAndGet();
    }

    public static void freeX509Chain(long certificateChain) {
    }
}
