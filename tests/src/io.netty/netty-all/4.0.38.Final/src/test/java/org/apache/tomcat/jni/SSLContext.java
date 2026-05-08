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

public final class SSLContext {
    private static final AtomicLong IDS = new AtomicLong(1000);
    private static final ConcurrentMap<Long, Integer> OPTIONS = new ConcurrentHashMap<Long, Integer>();
    private static final ConcurrentMap<Long, Integer> MODES = new ConcurrentHashMap<Long, Integer>();

    private SSLContext() {
    }

    public static long make(long pool, int protocol, int mode) {
        long context = IDS.incrementAndGet();
        MODES.put(context, mode);
        return context;
    }

    public static int free(long context) {
        OPTIONS.remove(context);
        MODES.remove(context);
        return 0;
    }

    public static void setContextId(long context, String id) {
    }

    public static void setBIO(long context, long bio, int direction) {
    }

    public static void setOptions(long context, int options) {
        OPTIONS.put(context, getOptions(context) | options);
    }

    public static int getOptions(long context) {
        Integer options = OPTIONS.get(context);
        return options == null ? 0 : options;
    }

    public static void clearOptions(long context, int options) {
        OPTIONS.put(context, getOptions(context) & ~options);
    }

    public static void setQuietShutdown(long context, boolean mode) {
    }

    public static boolean setCipherSuite(long context, String cipherSuite) {
        return true;
    }

    public static boolean setCARevocation(long context, String file, String path) {
        return true;
    }

    public static boolean setCertificateChainFile(long context, String file, boolean skipFirst) {
        return true;
    }

    public static boolean setCertificateChainBio(long context, long bio, boolean skipFirst) {
        return true;
    }

    public static boolean setCertificate(long context, String certificateFile, String keyFile, String password,
            int index) {
        return true;
    }

    public static boolean setCertificate(long context, String certificateFile, String keyFile, String password) {
        return true;
    }

    public static boolean setCertificateBio(long context, long certificateBio, long keyBio, String password,
            int index) {
        return true;
    }

    public static boolean setCertificateBio(long context, long certificateBio, long keyBio, String password) {
        return true;
    }

    public static long setSessionCacheSize(long context, long size) {
        return size == 0 ? 20480 : size;
    }

    public static long getSessionCacheSize(long context) {
        return 20480;
    }

    public static long setSessionCacheTimeout(long context, long timeout) {
        return timeout == 0 ? 300 : timeout;
    }

    public static long getSessionCacheTimeout(long context) {
        return 300;
    }

    public static long setSessionCacheMode(long context, long mode) {
        return mode;
    }

    public static long getSessionCacheMode(long context) {
        return 0;
    }

    public static long sessionAccept(long context) {
        return 0;
    }

    public static long sessionAcceptGood(long context) {
        return 0;
    }

    public static long sessionAcceptRenegotiate(long context) {
        return 0;
    }

    public static long sessionCacheFull(long context) {
        return 0;
    }

    public static long sessionCbHits(long context) {
        return 0;
    }

    public static long sessionConnect(long context) {
        return 0;
    }

    public static long sessionConnectGood(long context) {
        return 0;
    }

    public static long sessionConnectRenegotiate(long context) {
        return 0;
    }

    public static long sessionHits(long context) {
        return 0;
    }

    public static long sessionMisses(long context) {
        return 0;
    }

    public static long sessionNumber(long context) {
        return 0;
    }

    public static long sessionTimeouts(long context) {
        return 0;
    }

    public static long sessionTicketKeyNew(long context) {
        return 0;
    }

    public static long sessionTicketKeyResume(long context) {
        return 0;
    }

    public static long sessionTicketKeyRenew(long context) {
        return 0;
    }

    public static long sessionTicketKeyFail(long context) {
        return 0;
    }

    public static void setSessionTicketKeys(long context, SessionTicketKey[] keys) {
    }

    public static void setSessionTicketKeys(long context, byte[] keys) {
    }

    public static boolean setCACertificate(long context, String file, String path) {
        return true;
    }

    public static boolean setCACertificateBio(long context, long bio) {
        return true;
    }

    public static void setRandom(long context, String file) {
    }

    public static void setShutdownType(long context, int type) {
    }

    public static void setVerify(long context, int level, int depth) {
    }

    public static void setCertVerifyCallback(long context, CertificateVerifier verifier) {
    }

    public static void setCertRequestedCallback(long context, CertificateRequestedCallback callback) {
    }

    public static void setNextProtos(long context, String protocols) {
    }

    public static void setNpnProtos(long context, String[] protocols, int selectorFailureBehavior) {
    }

    public static void setAlpnProtos(long context, String[] protocols, int selectorFailureBehavior) {
    }

    public static void setTmpDH(long context, String dhFile) {
    }

    public static void setTmpDHLength(long context, int length) {
    }

    public static void setTmpECDHByCurveName(long context, String curveName) {
    }

    public static boolean setSessionIdContext(long context, byte[] sidCtx) {
        return true;
    }

    public static int setMode(long context, int mode) {
        MODES.put(context, getMode(context) | mode);
        return getMode(context);
    }

    public static int getMode(long context) {
        Integer mode = MODES.get(context);
        return mode == null ? 0 : mode;
    }
}
