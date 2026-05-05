/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
#include <jni.h>

static jstring rxtx_version(JNIEnv *env) {
    return (*env)->NewStringUTF(env, "test-native-rxtx");
}

JNIEXPORT jstring JNICALL Java_gnu_io_RXTXVersion_nativeGetVersion(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return rxtx_version(env);
}

JNIEXPORT jstring JNICALL Java_gnu_io_RXTXCommDriver_nativeGetVersion(JNIEnv *env, jclass clazz) {
    (void) clazz;
    return rxtx_version(env);
}

JNIEXPORT jstring JNICALL Java_gnu_io_RXTXCommDriver_getDeviceDirectory(JNIEnv *env, jobject driver) {
    (void) driver;
    return (*env)->NewStringUTF(env, "/dev/");
}

JNIEXPORT jboolean JNICALL Java_gnu_io_RXTXCommDriver_registerKnownPorts(JNIEnv *env, jobject driver, jint portType) {
    (void) env;
    (void) driver;
    (void) portType;
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_gnu_io_RXTXCommDriver_isPortPrefixValid(JNIEnv *env, jobject driver, jstring device) {
    (void) env;
    (void) driver;
    (void) device;
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_gnu_io_RXTXCommDriver_testRead(JNIEnv *env, jobject driver, jstring device, jint portType) {
    (void) env;
    (void) driver;
    (void) device;
    (void) portType;
    return JNI_FALSE;
}
