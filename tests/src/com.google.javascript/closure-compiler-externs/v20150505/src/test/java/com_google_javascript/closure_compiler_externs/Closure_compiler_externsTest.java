/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_javascript.closure_compiler_externs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Closure_compiler_externsTest {
    private static final String EXTERNS_RESOURCE = "externs.zip";
    private static final List<String> EXPECTED_EXTERN_FILES = List.of(
            "chrome.js",
            "deprecated.js",
            "es3.js",
            "es5.js",
            "es6.js",
            "es6_collections.js",
            "fileapi.js",
            "flash.js",
            "gecko_css.js",
            "gecko_dom.js",
            "gecko_event.js",
            "gecko_xml.js",
            "google.js",
            "html5.js",
            "ie_css.js",
            "ie_dom.js",
            "ie_event.js",
            "ie_vml.js",
            "intl.js",
            "iphone.js",
            "mediasource.js",
            "page_visibility.js",
            "v8.js",
            "w3c_anim_timing.js",
            "w3c_audio.js",
            "w3c_batterystatus.js",
            "w3c_css.js",
            "w3c_css3d.js",
            "w3c_device_sensor_event.js",
            "w3c_dom1.js",
            "w3c_dom2.js",
            "w3c_dom3.js",
            "w3c_elementtraversal.js",
            "w3c_encoding.js",
            "w3c_event.js",
            "w3c_event3.js",
            "w3c_geolocation.js",
            "w3c_indexeddb.js",
            "w3c_navigation_timing.js",
            "w3c_range.js",
            "w3c_rtc.js",
            "w3c_selectors.js",
            "w3c_webcrypto.js",
            "w3c_xml.js",
            "webgl.js",
            "webkit_css.js",
            "webkit_dom.js",
            "webkit_event.js",
            "webkit_notifications.js",
            "webstorage.js",
            "window.js");

    @Test
    void packagedExternsZipExposesCompleteV20150505FileSet() throws IOException {
        Map<String, String> externFiles = loadExternFiles();

        assertThat(externFiles.keySet()).containsExactlyInAnyOrderElementsOf(EXPECTED_EXTERN_FILES);
        assertThat(externFiles).hasSize(51);
        assertThat(externFiles.keySet()).allSatisfy(name -> assertThat(name).endsWith(".js"));
        assertThat(externFiles.values()).allSatisfy(content -> assertThat(content).isNotBlank());
    }

    @Test
    void everyExternFileIsAClosureCompilerExternDefinition() throws IOException {
        Map<String, String> externFiles = loadExternFiles();

        assertThat(externFiles).allSatisfy((name, content) -> assertThat(content)
                .as(name)
                .contains("Licensed under the Apache License, Version 2.0")
                .contains("@fileoverview")
                .contains("@externs"));
    }

    @Test
    void ecmascriptExternsDefineCoreLanguageFeatures() throws IOException {
        Map<String, String> externFiles = loadExternFiles();

        assertThat(externFiles.get("es3.js"))
                .contains("function Object(opt_value) {}")
                .contains("function Function(var_args) {}")
                .contains("function Array(var_args) {}")
                .contains("function Date(opt_yr_num, opt_mo_num, opt_day_num, opt_hr_num, opt_min_num,")
                .contains("var Math = {};")
                .contains("function RegExp(opt_pattern, opt_flags) {}");
        assertThat(externFiles.get("es5.js"))
                .contains("Function.prototype.bind = function(selfObj, var_args) {};")
                .contains("String.prototype.trim = function() {};")
                .contains("Object.create = function(proto, opt_properties) {};")
                .contains("Object.defineProperty = function(obj, prop, descriptor) {};")
                .contains("Object.keys = function(obj) {};")
                .contains("var JSON;");
        assertThat(externFiles.get("es6.js"))
                .contains("function Symbol(description) {}")
                .contains("function Iterable() {}")
                .contains("function ArrayBuffer(length) {}")
                .contains("function DataView(buffer, opt_byteOffset, opt_byteLength) {}")
                .contains("var Promise = function(resolver) {};");
        assertThat(externFiles.get("es6_collections.js"))
                .contains("function Map(opt_iterable) {}")
                .contains("function WeakMap(opt_iterable) {}")
                .contains("function Set(opt_iterable) {}")
                .contains("function WeakSet(opt_iterable) {}");
    }

    @Test
    void browserExternsDefineDomHtmlAndPlatformApis() throws IOException {
        Map<String, String> externFiles = loadExternFiles();

        assertThat(externFiles.get("window.js"))
                .contains("// Window properties")
                .contains("var top;")
                .contains("var navigator;")
                .contains("var document;")
                .contains("var location;");
        assertThat(externFiles.get("html5.js"))
                .contains("function HTMLCanvasElement() {}")
                .contains("HTMLCanvasElement.prototype.toDataURL = function(opt_type, var_args) {};")
                .contains("HTMLCanvasElement.prototype.getContext = function(contextId, opt_args) {};")
                .contains("function CanvasRenderingContext2D() {}")
                .contains("CanvasRenderingContext2D.prototype.fillRect = function(x, y, w, h) {};");
        assertThat(externFiles.get("w3c_xml.js"))
                .contains("function XMLHttpRequest() {}")
                .contains("XMLHttpRequest.prototype.open = function(method, url, opt_async, opt_user,")
                .contains("    opt_password) {};")
                .contains("XMLHttpRequest.prototype.send = function(opt_data) {};");
        assertThat(externFiles.get("webgl.js"))
                .contains("function WebGLRenderingContext() {}")
                .contains("WebGLRenderingContext.DEPTH_BUFFER_BIT;")
                .contains("WebGLRenderingContext.COLOR_BUFFER_BIT;")
                .contains("WebGLRenderingContext.prototype.createBuffer = function() {};");
        assertThat(externFiles.get("fileapi.js"))
                .contains("function FileReader() {}")
                .contains("FileReader.prototype.readAsArrayBuffer = function(blob) {};")
                .contains("function Blob(opt_blobParts, opt_options) {}");
    }

    @Test
    void storageExternsDefineWebStorageAndIndexedDbApis() throws IOException {
        Map<String, String> externFiles = loadExternFiles();

        assertThat(externFiles.get("webstorage.js"))
                .contains("function Storage() {}")
                .contains("Storage.prototype.key = function(index) {};")
                .contains("Storage.prototype.getItem = function(key) {};")
                .contains("Storage.prototype.setItem = function(key, data) {};")
                .contains("Storage.prototype.removeItem = function(key) {};")
                .contains("Storage.prototype.clear = function() {};")
                .contains("Window.prototype.localStorage;")
                .contains("Window.prototype.sessionStorage;")
                .contains("function StorageEvent() {}");
        assertThat(externFiles.get("w3c_indexeddb.js"))
                .contains("function IDBFactory() {}")
                .contains("IDBFactory.prototype.open = function(name, opt_version) {};")
                .contains("function IDBDatabase() {}")
                .contains("IDBDatabase.prototype.createObjectStore =")
                .contains("IDBDatabase.prototype.transaction = function(storeNames, mode) {};")
                .contains("function IDBObjectStore() {}")
                .contains("IDBObjectStore.prototype.put = function(value, key) {};")
                .contains("IDBObjectStore.prototype.createIndex = function(name, keyPath, opt_paramters) {};")
                .contains("function IDBIndex() {}")
                .contains("IDBIndex.prototype.openCursor = function(range, direction) {};")
                .contains("function IDBTransaction() {}")
                .contains("IDBTransaction.prototype.objectStore = function(name) {};");
    }

    @Test
    void webCryptoExternsDefineCryptoKeysAndSubtleOperations() throws IOException {
        Map<String, String> externFiles = loadExternFiles();

        assertThat(externFiles.get("w3c_webcrypto.js"))
                .contains("var webCrypto = {};")
                .contains("webCrypto.Algorithm;")
                .contains("webCrypto.AlgorithmIdentifier;")
                .contains("webCrypto.CryptoKey = function() {};")
                .contains("webCrypto.CryptoKey.prototype.type;")
                .contains("webCrypto.CryptoKey.prototype.extractable;")
                .contains("webCrypto.CryptoKey.prototype.algorithm;")
                .contains("webCrypto.CryptoKey.prototype.usages;")
                .contains("webCrypto.JsonWebKey = function() {};")
                .contains("webCrypto.SubtleCrypto = function() {};")
                .contains("webCrypto.SubtleCrypto.prototype.encrypt = function(algorithm, key,")
                .contains("webCrypto.SubtleCrypto.prototype.decrypt = function(algorithm, key,")
                .contains("webCrypto.SubtleCrypto.prototype.sign = function(algorithm, key,")
                .contains("webCrypto.SubtleCrypto.prototype.verify = function(algorithm, key,")
                .contains("webCrypto.SubtleCrypto.prototype.digest = function(algorithm, data) {};")
                .contains("webCrypto.SubtleCrypto.prototype.generateKey = function(algorithm,")
                .contains("webCrypto.SubtleCrypto.prototype.importKey = function(format, keyData,")
                .contains("webCrypto.SubtleCrypto.prototype.exportKey = function(format, key) {};")
                .contains("Window.prototype.crypto.getRandomValues = function(typedArray) {};")
                .contains("Window.prototype.crypto.subtle;");
    }

    private static Map<String, String> loadExternFiles() throws IOException {
        Map<String, String> externFiles = new LinkedHashMap<>();

        try (InputStream resourceStream = openExternsResource();
                ZipInputStream zipInputStream = new ZipInputStream(resourceStream, StandardCharsets.UTF_8)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                assertThat(entry.isDirectory()).as(entry.getName()).isFalse();
                String content = readCurrentEntry(zipInputStream);
                String previousContent = externFiles.put(entry.getName(), content);
                assertThat(previousContent).as("duplicate entry %s", entry.getName()).isNull();
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }

        return externFiles;
    }

    private static InputStream openExternsResource() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream resourceStream = classLoader.getResourceAsStream(EXTERNS_RESOURCE);
        assertThat(resourceStream).as("classpath resource %s", EXTERNS_RESOURCE).isNotNull();
        return resourceStream;
    }

    private static String readCurrentEntry(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read = inputStream.read(buffer);
        while (read != -1) {
            outputStream.write(buffer, 0, read);
            read = inputStream.read(buffer);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
