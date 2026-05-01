/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_android_tools_build.builder_model;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AdbOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.LintOptions;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.PackagingOptions;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.Version;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Builder_modelTest {
    private static final File PROJECT_DIR = new File("sample-project");

    @Test
    public void exposesStableVersionArtifactOutputAndSeverityConstants() {
        assertThat(Version.ANDROID_GRADLE_PLUGIN_VERSION).isEqualTo("1.5.0");
        assertThat(Version.BUILDER_MODEL_API_VERSION).isEqualTo(3);

        assertThat(AndroidProject.PROPERTY_BUILD_MODEL_ONLY).isEqualTo("android.injected.build.model.only");
        assertThat(AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED)
                .isEqualTo("android.injected.build.model.only.advanced");
        assertThat(AndroidProject.PROPERTY_BUILD_API).isEqualTo("android.injected.build.api");
        assertThat(AndroidProject.PROPERTY_BUILD_ARCH).isEqualTo("android.injected.build.arch");
        assertThat(AndroidProject.PROPERTY_INVOKED_FROM_IDE).isEqualTo("android.injected.invoked.from.ide");
        assertThat(AndroidProject.PROPERTY_SIGNING_STORE_FILE).isEqualTo("android.injected.signing.store.file");
        assertThat(AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD).isEqualTo("android.injected.signing.store.password");
        assertThat(AndroidProject.PROPERTY_SIGNING_KEY_ALIAS).isEqualTo("android.injected.signing.key.alias");
        assertThat(AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD).isEqualTo("android.injected.signing.key.password");
        assertThat(AndroidProject.PROPERTY_SIGNING_STORE_TYPE).isEqualTo("android.injected.signing.store.type");
        assertThat(AndroidProject.PROPERTY_APK_LOCATION).isEqualTo("android.injected.apk.location");
        assertThat(AndroidProject.ARTIFACT_MAIN).isEqualTo("_main_");
        assertThat(AndroidProject.ARTIFACT_ANDROID_TEST).isEqualTo("_android_test_");
        assertThat(AndroidProject.ARTIFACT_UNIT_TEST).isEqualTo("_unit_test_");
        assertThat(Arrays.asList(AndroidProject.FD_INTERMEDIATES, AndroidProject.FD_LOGS,
                AndroidProject.FD_OUTPUTS, AndroidProject.FD_GENERATED))
                .containsExactly("intermediates", "logs", "outputs", "generated");

        assertThat(OutputFile.NO_FILTER).isNull();
        assertThat(OutputFile.MAIN).isEqualTo(OutputFile.OutputType.MAIN.name());
        assertThat(OutputFile.FULL_SPLIT).isEqualTo(OutputFile.OutputType.FULL_SPLIT.name());
        assertThat(OutputFile.SPLIT).isEqualTo(OutputFile.OutputType.SPLIT.name());
        assertThat(OutputFile.DENSITY).isEqualTo(OutputFile.FilterType.DENSITY.name());
        assertThat(OutputFile.ABI).isEqualTo(OutputFile.FilterType.ABI.name());
        assertThat(OutputFile.LANGUAGE).isEqualTo(OutputFile.FilterType.LANGUAGE.name());

        assertThat(ArtifactMetaData.TYPE_ANDROID).isEqualTo(1);
        assertThat(ArtifactMetaData.TYPE_JAVA).isEqualTo(2);
        assertThat(LintOptions.SEVERITY_FATAL).isEqualTo(1);
        assertThat(LintOptions.SEVERITY_ERROR).isEqualTo(2);
        assertThat(LintOptions.SEVERITY_WARNING).isEqualTo(3);
        assertThat(LintOptions.SEVERITY_INFORMATIONAL).isEqualTo(4);
        assertThat(LintOptions.SEVERITY_IGNORE).isEqualTo(5);
        assertThat(SyncIssue.SEVERITY_WARNING).isEqualTo(1);
        assertThat(SyncIssue.SEVERITY_ERROR).isEqualTo(2);
        assertThat(SyncIssue.TYPE_GENERIC).isEqualTo(0);
        assertThat(SyncIssue.TYPE_MAX).isEqualTo(SyncIssue.TYPE_JACK_IS_NOT_SUPPORTED);
    }

    @Test
    public void classifiesSplitOutputsByTypedOutputAndFilterNames() {
        SimpleFilterData x86Filter = new SimpleFilterData(OutputFile.FilterType.ABI.name(), "x86");
        SimpleFilterData englishFilter = new SimpleFilterData(OutputFile.FilterType.LANGUAGE.name(), "en");
        SimpleOutputFile mainOutput = new SimpleOutputFile(
                OutputFile.OutputType.MAIN.name(), file("outputs/apk/demo-debug.apk"),
                Collections.<FilterData>emptyList());
        SimpleOutputFile abiLanguageSplit = new SimpleOutputFile(
                OutputFile.OutputType.SPLIT.name(), file("outputs/apk/demo-x86-en-debug.apk"),
                Arrays.<FilterData>asList(x86Filter, englishFilter));
        SimpleOutputFile fullSplit = new SimpleOutputFile(
                OutputFile.OutputType.FULL_SPLIT.name(), file("outputs/apk/demo-universal-debug.apk"),
                Collections.<FilterData>emptyList());
        Collection<OutputFile> outputFiles = Arrays.<OutputFile>asList(mainOutput, abiLanguageSplit, fullSplit);

        Map<String, OutputFile> splitOutputsByAbi = new LinkedHashMap<String, OutputFile>();
        for (OutputFile outputFile : outputFiles) {
            OutputFile.OutputType outputType = OutputFile.OutputType.valueOf(outputFile.getOutputType());
            if (outputType != OutputFile.OutputType.SPLIT) {
                continue;
            }
            for (FilterData filter : outputFile.getFilters()) {
                OutputFile.FilterType filterType = OutputFile.FilterType.valueOf(filter.getFilterType());
                if (filterType == OutputFile.FilterType.ABI) {
                    splitOutputsByAbi.put(filter.getIdentifier(), outputFile);
                }
            }
        }

        assertThat(splitOutputsByAbi).containsOnlyKeys("x86");
        OutputFile x86Split = splitOutputsByAbi.get("x86");
        assertThat(x86Split.getOutputFile()).isEqualTo(file("outputs/apk/demo-x86-en-debug.apk"));
        assertThat(x86Split.getFilterTypes()).containsExactly(OutputFile.ABI, OutputFile.LANGUAGE);
        assertThat(x86Split.getFilters()).containsExactly(x86Filter, englishFilter);
    }

    @Test
    public void groupsGeneratedApksThroughVariantOutputContract() {
        SimpleOutputFile demoMainOutput = new SimpleOutputFile(
                OutputFile.MAIN, file("outputs/apk/demo-debug.apk"), Collections.<FilterData>emptyList());
        SimpleOutputFile demoDensitySplit = new SimpleOutputFile(
                OutputFile.SPLIT, file("outputs/apk/demo-xxhdpi-debug.apk"), Collections.<FilterData>singletonList(
                        new SimpleFilterData(OutputFile.DENSITY, "xxhdpi")));
        SimpleOutputFile demoLanguageSplit = new SimpleOutputFile(
                OutputFile.SPLIT, file("outputs/apk/demo-en-debug.apk"), Collections.<FilterData>singletonList(
                        new SimpleFilterData(OutputFile.LANGUAGE, "en")));
        SimpleVariantOutput demoOutput = new SimpleVariantOutput(
                demoMainOutput, Arrays.<OutputFile>asList(demoDensitySplit, demoLanguageSplit),
                file("outputs/splits/demo"), 10);
        SimpleOutputFile paidMainOutput = new SimpleOutputFile(
                OutputFile.MAIN, file("outputs/apk/paid-debug.apk"), Collections.<FilterData>emptyList());
        SimpleVariantOutput paidOutput = new SimpleVariantOutput(
                paidMainOutput, Collections.<OutputFile>emptyList(), file("outputs/splits/paid"), 20);
        Collection<VariantOutput> variantOutputs = Arrays.<VariantOutput>asList(demoOutput, paidOutput);

        Map<Integer, File> mainOutputFilesByVersionCode = new LinkedHashMap<Integer, File>();
        Map<File, Collection<OutputFile>> splitOutputsByFolder = new LinkedHashMap<File, Collection<OutputFile>>();
        for (VariantOutput variantOutput : variantOutputs) {
            mainOutputFilesByVersionCode.put(variantOutput.getVersionCode(), variantOutput.getMainOutputFile()
                    .getOutputFile());
            splitOutputsByFolder.put(variantOutput.getSplitFolder(), new ArrayList<OutputFile>(
                    variantOutput.getOutputs()));
        }

        assertThat(mainOutputFilesByVersionCode)
                .containsEntry(10, file("outputs/apk/demo-debug.apk"))
                .containsEntry(20, file("outputs/apk/paid-debug.apk"));
        assertThat(splitOutputsByFolder.get(file("outputs/splits/demo")))
                .containsExactly(demoDensitySplit, demoLanguageSplit);
        assertThat(splitOutputsByFolder.get(file("outputs/splits/paid"))).isEmpty();
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    public void traversesCompleteAndroidProjectModelGraph() {
        SimpleSigningConfig signingConfig = new SimpleSigningConfig(
                "debug", file("keystore/debug.keystore"), "store", "androiddebugkey", "key", "jks", true);
        SimpleSourceProvider mainSourceProvider = new SimpleSourceProvider("main", "src/main");
        SimpleSourceProvider androidTestSourceProvider = new SimpleSourceProvider("androidTest", "src/androidTest");
        SimpleSourceProviderContainer androidTestSources = new SimpleSourceProviderContainer(
                AndroidProject.ARTIFACT_ANDROID_TEST, androidTestSourceProvider);
        SimpleClassField applicationIdField = new SimpleClassField(
                "String", "APPLICATION_ID", "\"com.example.demo\"", "Application id constant");
        SimpleBuildType debugBuildType = new SimpleBuildType("debug", signingConfig, applicationIdField);
        SimpleProductFlavor demoFlavor = new SimpleProductFlavor("demo", signingConfig, applicationIdField);
        SimpleProductFlavorContainer defaultConfig = new SimpleProductFlavorContainer(
                demoFlavor, mainSourceProvider, androidTestSources);
        SimpleBuildTypeContainer buildTypeContainer = new SimpleBuildTypeContainer(
                debugBuildType, mainSourceProvider, androidTestSources);
        SimpleMavenCoordinates requestedCoordinates = new SimpleMavenCoordinates(
                "com.example", "requested", "1.0", "aar", null);
        SimpleMavenCoordinates resolvedCoordinates = new SimpleMavenCoordinates(
                "com.example", "resolved", "1.1", "aar", "debug");
        SimpleJavaLibrary transitiveJavaLibrary = new SimpleJavaLibrary(
                file("libs/transitive.jar"), requestedCoordinates, resolvedCoordinates,
                Collections.<JavaLibrary>emptyList(), false);
        SimpleJavaLibrary directJavaLibrary = new SimpleJavaLibrary(
                file("libs/direct.jar"), requestedCoordinates, resolvedCoordinates,
                Collections.<JavaLibrary>singletonList(transitiveJavaLibrary), true);
        SimpleAndroidLibrary androidLibrary = new SimpleAndroidLibrary(
                ":library", "debug", requestedCoordinates, resolvedCoordinates,
                Collections.<AndroidLibrary>emptyList(), true);
        SimpleDependencies dependencies = new SimpleDependencies(
                Collections.<AndroidLibrary>singletonList(androidLibrary),
                Collections.<JavaLibrary>singletonList(directJavaLibrary), Collections.singletonList(":shared"));
        SimpleFilterData densityFilter = new SimpleFilterData(OutputFile.DENSITY, "xxhdpi");
        SimpleOutputFile mainOutputFile = new SimpleOutputFile(
                OutputFile.MAIN, file("outputs/apk/demo-debug.apk"), Collections.<FilterData>emptyList());
        SimpleOutputFile splitOutputFile = new SimpleOutputFile(
                OutputFile.SPLIT, file("outputs/apk/demo-x86-debug.apk"),
                Collections.<FilterData>singletonList(densityFilter));
        SimpleAndroidArtifactOutput artifactOutput = new SimpleAndroidArtifactOutput(
                mainOutputFile, Collections.<OutputFile>singletonList(splitOutputFile), file("outputs/splits"), 42,
                "assembleDemoDebug", file("intermediates/manifests/full/demo/debug/AndroidManifest.xml"));
        SimpleNativeLibrary nativeLibrary = new SimpleNativeLibrary("demo-native", "x86", "clang");
        SimpleAndroidArtifact mainArtifact = new SimpleAndroidArtifact(
                AndroidProject.ARTIFACT_MAIN, dependencies, mainSourceProvider, androidTestSourceProvider,
                Collections.<AndroidArtifactOutput>singletonList(artifactOutput), Collections.singleton("x86"),
                nativeLibrary, applicationIdField);
        SimpleJavaArtifact unitTestArtifact = new SimpleJavaArtifact(
                AndroidProject.ARTIFACT_UNIT_TEST, dependencies, mainSourceProvider, androidTestSourceProvider,
                file("mockable/android.jar"));
        SimpleVariant variant = new SimpleVariant(
                "demoDebug", "Demo debug", "debug", Collections.singletonList("demo"), demoFlavor, mainArtifact,
                unitTestArtifact);
        SimpleArtifactMetaData extraArtifact = new SimpleArtifactMetaData(
                AndroidProject.ARTIFACT_UNIT_TEST, true, ArtifactMetaData.TYPE_JAVA);
        SimpleNativeToolchain toolchain = new SimpleNativeToolchain(
                "clang", file("toolchains/clang"), file("toolchains/clang++"));
        SimpleAndroidProject project = new SimpleAndroidProject(
                "DemoProject", defaultConfig, buildTypeContainer, variant, extraArtifact, toolchain, signingConfig);

        assertThat(project.getModelVersion()).isEqualTo(Version.ANDROID_GRADLE_PLUGIN_VERSION);
        assertThat(project.getApiVersion()).isEqualTo(Version.BUILDER_MODEL_API_VERSION);
        assertThat(project.getName()).isEqualTo("DemoProject");
        assertThat(project.isLibrary()).isFalse();
        assertThat(project.getDefaultConfig().getProductFlavor().getApplicationId()).isEqualTo("com.example.demo");
        assertThat(project.getDefaultConfig().getSourceProvider().getManifestFile())
                .isEqualTo(file("src/main/AndroidManifest.xml"));
        assertThat(project.getDefaultConfig().getExtraSourceProviders()).containsExactly(androidTestSources);
        assertThat(project.getBuildTypes()).containsExactly(buildTypeContainer);
        assertThat(project.getProductFlavors()).containsExactly(defaultConfig);
        assertThat(project.getFlavorDimensions()).containsExactly("environment");
        assertThat(project.getExtraArtifacts()).containsExactly(extraArtifact);
        assertThat(project.getCompileTarget()).isEqualTo("android-23");
        assertThat(project.getBootClasspath()).containsExactly("android.jar", "optional/org.apache.http.legacy.jar");
        assertThat(project.getFrameworkSources()).containsExactly(file("frameworks/base/core/java"));
        assertThat(project.getNativeToolchains()).containsExactly(toolchain);
        assertThat(project.getSigningConfigs()).containsExactly(signingConfig);
        assertThat(project.getAaptOptions().getNoCompress()).containsExactly("webp", "mp3");
        assertThat(project.getLintOptions().getSeverityOverrides()).containsEntry("NewApi", LintOptions.SEVERITY_ERROR);
        assertThat(project.getJavaCompileOptions().getEncoding()).isEqualTo("UTF-8");
        SyncIssue projectSyncIssue = project.getSyncIssues().iterator().next();
        assertThat(projectSyncIssue.getType()).isEqualTo(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        assertThat(project.getBuildFolder()).isEqualTo(file("build"));
        assertThat(project.getResourcePrefix()).isEqualTo("demo_");

        Variant firstVariant = project.getVariants().iterator().next();
        assertThat(firstVariant.getName()).isEqualTo("demoDebug");
        assertThat(firstVariant.getDisplayName()).isEqualTo("Demo debug");
        assertThat(firstVariant.getBuildType()).isEqualTo("debug");
        assertThat(firstVariant.getProductFlavors()).containsExactly("demo");
        assertThat(firstVariant.getMergedFlavor().getMinSdkVersion().getApiString()).isEqualTo("21");
        assertThat(firstVariant.getExtraJavaArtifacts()).containsExactly(unitTestArtifact);
        assertThat(firstVariant.getExtraAndroidArtifacts()).isEmpty();
        assertBaseConfig(debugBuildType, "debug");
        assertThat(debugBuildType.isDebuggable()).isTrue();
        assertThat(debugBuildType.isTestCoverageEnabled()).isTrue();
        assertThat(debugBuildType.isPseudoLocalesEnabled()).isFalse();
        assertThat(debugBuildType.isJniDebuggable()).isTrue();
        assertThat(debugBuildType.isRenderscriptDebuggable()).isTrue();
        assertThat(debugBuildType.getRenderscriptOptimLevel()).isEqualTo(3);
        assertThat(debugBuildType.getVersionNameSuffix()).isEqualTo("-debug");
        assertThat(debugBuildType.isMinifyEnabled()).isFalse();
        assertThat(debugBuildType.isZipAlignEnabled()).isTrue();
        assertThat(debugBuildType.isEmbedMicroApp()).isTrue();
        assertThat(debugBuildType.getSigningConfig()).isEqualTo(signingConfig);
        assertThat(buildTypeContainer.getBuildType()).isEqualTo(debugBuildType);
        assertThat(buildTypeContainer.getSourceProvider()).isEqualTo(mainSourceProvider);
        assertThat(buildTypeContainer.getExtraSourceProviders()).containsExactly(androidTestSources);

        assertBaseConfig(demoFlavor, "demo");
        assertThat(demoFlavor.getVersionCode()).isEqualTo(42);
        assertThat(demoFlavor.getVersionName()).isEqualTo("1.0-demo");
        assertThat(demoFlavor.getTargetSdkVersion().getCodename()).isEqualTo("MNC");
        assertThat(demoFlavor.getMaxSdkVersion()).isEqualTo(28);
        assertThat(demoFlavor.getRenderscriptTargetApi()).isEqualTo(21);
        assertThat(demoFlavor.getRenderscriptSupportModeEnabled()).isTrue();
        assertThat(demoFlavor.getRenderscriptNdkModeEnabled()).isFalse();
        assertThat(demoFlavor.getTestApplicationId()).isEqualTo("com.example.demo.test");
        assertThat(demoFlavor.getTestInstrumentationRunner()).contains("AndroidJUnitRunner");
        assertThat(demoFlavor.getTestInstrumentationRunnerArguments()).containsEntry("clearPackageData", "true");
        assertThat(demoFlavor.getTestHandleProfiling()).isFalse();
        assertThat(demoFlavor.getTestFunctionalTest()).isTrue();
        assertThat(demoFlavor.getResourceConfigurations()).containsExactly("en", "xxhdpi");
        assertThat(demoFlavor.getSigningConfig()).isEqualTo(signingConfig);
        assertThat(demoFlavor.getGeneratedDensities()).containsExactly("mdpi", "xxhdpi");
        assertThat(demoFlavor.getDimension()).isEqualTo("environment");

        assertThat(mainSourceProvider.getName()).isEqualTo("main");
        assertThat(mainSourceProvider.getJavaDirectories()).containsExactly(file("src/main/java"));
        assertThat(mainSourceProvider.getResourcesDirectories()).containsExactly(file("src/main/resources"));
        assertThat(mainSourceProvider.getAidlDirectories()).containsExactly(file("src/main/aidl"));
        assertThat(mainSourceProvider.getRenderscriptDirectories()).containsExactly(file("src/main/rs"));
        assertThat(mainSourceProvider.getCDirectories()).containsExactly(file("src/main/c"));
        assertThat(mainSourceProvider.getCppDirectories()).containsExactly(file("src/main/cpp"));
        assertThat(mainSourceProvider.getResDirectories()).containsExactly(file("src/main/res"));
        assertThat(mainSourceProvider.getAssetsDirectories()).containsExactly(file("src/main/assets"));
        assertThat(mainSourceProvider.getJniLibsDirectories()).containsExactly(file("src/main/jniLibs"));
        assertThat(androidTestSources.getArtifactName()).isEqualTo(AndroidProject.ARTIFACT_ANDROID_TEST);
        assertThat(androidTestSources.getSourceProvider()).isEqualTo(androidTestSourceProvider);

        assertThat(signingConfig.getStoreFile()).isEqualTo(file("keystore/debug.keystore"));
        assertThat(signingConfig.getStorePassword()).isEqualTo("store");
        assertThat(signingConfig.getKeyAlias()).isEqualTo("androiddebugkey");
        assertThat(signingConfig.getKeyPassword()).isEqualTo("key");
        assertThat(signingConfig.getStoreType()).isEqualTo("jks");
        assertThat(signingConfig.isSigningReady()).isTrue();
        assertThat(applicationIdField.getType()).isEqualTo("String");
        assertThat(applicationIdField.getValue()).isEqualTo("\"com.example.demo\"");
        assertThat(applicationIdField.getDocumentation()).isEqualTo("Application id constant");
        assertThat(applicationIdField.getAnnotations()).containsExactly("Deprecated", "Generated");
        assertThat(extraArtifact.getName()).isEqualTo(AndroidProject.ARTIFACT_UNIT_TEST);
        assertThat(extraArtifact.isTest()).isTrue();
        assertThat(extraArtifact.getType()).isEqualTo(ArtifactMetaData.TYPE_JAVA);

        AndroidArtifact artifact = firstVariant.getMainArtifact();
        assertThat(artifact.getName()).isEqualTo(AndroidProject.ARTIFACT_MAIN);
        assertThat(artifact.getCompileTaskName()).isEqualTo("compile_main_JavaWithJavac");
        assertThat(artifact.getAssembleTaskName()).isEqualTo("assemble_main_");
        assertThat(artifact.getClassesFolder()).isEqualTo(file("build/intermediates/classes/_main_"));
        assertThat(artifact.getJavaResourcesFolder()).isEqualTo(file("build/intermediates/javaResources/_main_"));
        assertThat(artifact.getVariantSourceProvider()).isEqualTo(mainSourceProvider);
        assertThat(artifact.getMultiFlavorSourceProvider()).isEqualTo(androidTestSourceProvider);
        assertThat(artifact.getIdeSetupTaskNames()).containsExactly("generate_main_Sources");
        assertThat(artifact.getGeneratedSourceFolders()).containsExactly(file("build/generated/source/r/_main_"));
        assertThat(artifact.getGeneratedResourceFolders())
                .containsExactly(file("build/generated/res/resValues/_main_"));
        assertThat(artifact.isSigned()).isTrue();
        assertThat(artifact.getSigningConfigName()).isEqualTo("debug");
        assertThat(artifact.getApplicationId()).isEqualTo("com.example.demo");
        assertThat(artifact.getSourceGenTaskName()).isEqualTo("generate_main_Sources");
        assertThat(artifact.getAbiFilters()).containsExactly("x86");
        assertThat(artifact.getNativeLibraries()).containsExactly(nativeLibrary);
        assertThat(artifact.getBuildConfigFields()).containsEntry("APPLICATION_ID", applicationIdField);
        assertThat(artifact.getResValues()).containsEntry("APPLICATION_ID", applicationIdField);

        Dependencies artifactDependencies = artifact.getDependencies();
        assertThat(artifactDependencies.getLibraries()).containsExactly(androidLibrary);
        assertThat(artifactDependencies.getJavaLibraries()).containsExactly(directJavaLibrary);
        assertThat(artifactDependencies.getProjects()).containsExactly(":shared");
        assertThat(directJavaLibrary.getJarFile()).isEqualTo(file("libs/direct.jar"));
        List<? extends JavaLibrary> directJavaDependencies = directJavaLibrary.getDependencies();
        assertThat(directJavaDependencies).hasSize(1);
        assertThat(directJavaDependencies.iterator().next()).isSameAs(transitiveJavaLibrary);
        assertThat(directJavaLibrary.isProvided()).isTrue();
        assertThat(androidLibrary.getProject()).isEqualTo(":library");
        assertThat(androidLibrary.getProjectVariant()).isEqualTo("debug");
        assertThat(androidLibrary.getBundle()).isEqualTo(file("libs/library.aar"));
        assertThat(androidLibrary.getFolder()).isEqualTo(file("exploded-aar/library"));
        assertThat(androidLibrary.getLibraryDependencies()).isEmpty();
        assertThat(androidLibrary.getManifest()).isEqualTo(file("exploded-aar/library/AndroidManifest.xml"));
        assertThat(androidLibrary.getJarFile()).isEqualTo(file("exploded-aar/library/classes.jar"));
        assertThat(androidLibrary.getLocalJars()).containsExactly(file("exploded-aar/library/libs/local.jar"));
        assertThat(androidLibrary.getResFolder()).isEqualTo(file("exploded-aar/library/res"));
        assertThat(androidLibrary.getAssetsFolder()).isEqualTo(file("exploded-aar/library/assets"));
        assertThat(androidLibrary.getJniFolder()).isEqualTo(file("exploded-aar/library/jni"));
        assertThat(androidLibrary.getAidlFolder()).isEqualTo(file("exploded-aar/library/aidl"));
        assertThat(androidLibrary.getRenderscriptFolder()).isEqualTo(file("exploded-aar/library/rs"));
        assertThat(androidLibrary.getProguardRules()).isEqualTo(file("exploded-aar/library/proguard.txt"));
        assertThat(androidLibrary.getLintJar()).isEqualTo(file("exploded-aar/library/lint.jar"));
        assertThat(androidLibrary.getExternalAnnotations()).isEqualTo(file("exploded-aar/library/annotations.zip"));
        assertThat(androidLibrary.getPublicResources()).isEqualTo(file("exploded-aar/library/public.txt"));
        assertThat(androidLibrary.isOptional()).isTrue();
        assertThat(((Library) androidLibrary).getRequestedCoordinates().getArtifactId()).isEqualTo("requested");
        assertThat(((Library) androidLibrary).getResolvedCoordinates().getClassifier()).isEqualTo("debug");

        AndroidArtifactOutput output = artifact.getOutputs().iterator().next();
        assertThat(output.getMainOutputFile()).isEqualTo(mainOutputFile);
        assertThat(mainOutputFile.getOutputType()).isEqualTo(OutputFile.MAIN);
        assertThat(mainOutputFile.getOutputFile()).isEqualTo(file("outputs/apk/demo-debug.apk"));
        assertThat(mainOutputFile.getFilterTypes()).isEmpty();
        Collection<? extends OutputFile> outputFiles = output.getOutputs();
        assertThat(outputFiles).hasSize(1);
        assertThat(outputFiles.iterator().next()).isSameAs(splitOutputFile);
        assertThat(output.getSplitFolder()).isEqualTo(file("outputs/splits"));
        assertThat(output.getVersionCode()).isEqualTo(42);
        assertThat(output.getAssembleTaskName()).isEqualTo("assembleDemoDebug");
        assertThat(output.getGeneratedManifest())
                .isEqualTo(file("intermediates/manifests/full/demo/debug/AndroidManifest.xml"));
        assertThat(splitOutputFile.getFilterTypes()).containsExactly(OutputFile.DENSITY);
        assertThat(splitOutputFile.getFilters()).containsExactly(densityFilter);
        assertThat(splitOutputFile.getOutputFile()).isEqualTo(file("outputs/apk/demo-x86-debug.apk"));
        assertThat(densityFilter.getIdentifier()).isEqualTo("xxhdpi");
        assertThat(unitTestArtifact.getMockablePlatformJar()).isEqualTo(file("mockable/android.jar"));
        assertThat(nativeLibrary.getCIncludeDirs()).containsExactly(file("src/main/c/include"));
        assertThat(nativeLibrary.getCppIncludeDirs()).containsExactly(file("src/main/cpp/include"));
        assertThat(nativeLibrary.getCSystemIncludeDirs()).containsExactly(file("ndk/sysroot/usr/include"));
        assertThat(nativeLibrary.getCppSystemIncludeDirs())
                .containsExactly(file("ndk/sources/cxx-stl/gnu-libstdc++/include"));
        assertThat(nativeLibrary.getCDefines()).containsExactly("ANDROID");
        assertThat(nativeLibrary.getCppDefines()).containsExactly("ANDROID_CPP");
        assertThat(nativeLibrary.getCCompilerFlags()).containsExactly("-Wall");
        assertThat(nativeLibrary.getCppCompilerFlags()).containsExactly("-std=c++11");
        assertThat(nativeLibrary.getDebuggableLibraryFolders())
                .containsExactly(file("build/intermediates/ndk/debug/lib/x86"));
    }

    @Test
    public void exposesConfigurationOptionsAndDiagnosticsThroughInterfaces() {
        SimpleAaptOptions aaptOptions = new SimpleAaptOptions();
        SimpleAdbOptions adbOptions = new SimpleAdbOptions();
        SimpleDataBindingOptions dataBindingOptions = new SimpleDataBindingOptions();
        SimplePackagingOptions packagingOptions = new SimplePackagingOptions();
        SimpleJavaCompileOptions javaCompileOptions = new SimpleJavaCompileOptions();
        SimpleLintOptions lintOptions = new SimpleLintOptions();
        SimpleSyncIssue syncIssue = new SimpleSyncIssue(
                SyncIssue.SEVERITY_ERROR, SyncIssue.TYPE_JAR_DEPEND_ON_AAR, "com.example:aar:1.0",
                "A jar dependency depends on an aar artifact");

        String defaultIgnoreAssets = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~";
        assertThat(aaptOptions.getIgnoreAssets()).isEqualTo(defaultIgnoreAssets);
        assertThat(aaptOptions.getNoCompress()).containsExactly("webp", "mp3");
        assertThat(aaptOptions.getFailOnMissingConfigEntry()).isTrue();
        assertThat(aaptOptions.getAdditionalParameters()).containsExactly("--auto-add-overlay", "--no-version-vectors");

        assertThat(adbOptions.getTimeOutInMs()).isEqualTo(30_000);
        assertThat(adbOptions.getInstallOptions()).containsExactly("-r", "-d");

        assertThat(dataBindingOptions.getVersion()).isEqualTo("1.0-rc4");
        assertThat(dataBindingOptions.isEnabled()).isTrue();
        assertThat(dataBindingOptions.getAddDefaultAdapters()).isFalse();

        assertThat(packagingOptions.getExcludes()).containsExactly("META-INF/LICENSE");
        assertThat(packagingOptions.getPickFirsts()).containsExactly("lib/x86/libshared.so");
        assertThat(packagingOptions.getMerges())
                .containsExactly("META-INF/services/javax.annotation.processing.Processor");

        assertThat(javaCompileOptions.getEncoding()).isEqualTo("UTF-8");
        assertThat(javaCompileOptions.getSourceCompatibility()).isEqualTo("1.7");
        assertThat(javaCompileOptions.getTargetCompatibility()).isEqualTo("1.7");

        assertThat(lintOptions.getDisable()).containsExactly("TypographyFractions");
        assertThat(lintOptions.getEnable()).containsExactly("NewApi");
        assertThat(lintOptions.getCheck()).containsExactly("UnusedResources");
        assertThat(lintOptions.isAbortOnError()).isTrue();
        assertThat(lintOptions.isAbsolutePaths()).isFalse();
        assertThat(lintOptions.isNoLines()).isTrue();
        assertThat(lintOptions.isQuiet()).isFalse();
        assertThat(lintOptions.isCheckAllWarnings()).isTrue();
        assertThat(lintOptions.isIgnoreWarnings()).isFalse();
        assertThat(lintOptions.isWarningsAsErrors()).isTrue();
        assertThat(lintOptions.isExplainIssues()).isTrue();
        assertThat(lintOptions.isShowAll()).isFalse();
        assertThat(lintOptions.getLintConfig()).isEqualTo(file("lint.xml"));
        assertThat(lintOptions.getTextReport()).isTrue();
        assertThat(lintOptions.getTextOutput()).isEqualTo(file("reports/lint-results.txt"));
        assertThat(lintOptions.getHtmlReport()).isTrue();
        assertThat(lintOptions.getHtmlOutput()).isEqualTo(file("reports/lint-results.html"));
        assertThat(lintOptions.getXmlReport()).isTrue();
        assertThat(lintOptions.getXmlOutput()).isEqualTo(file("reports/lint-results.xml"));
        assertThat(lintOptions.isCheckReleaseBuilds()).isTrue();
        assertThat(lintOptions.getSeverityOverrides()).containsEntry("NewApi", LintOptions.SEVERITY_ERROR);

        assertThat(syncIssue.getSeverity()).isEqualTo(SyncIssue.SEVERITY_ERROR);
        assertThat(syncIssue.getType()).isEqualTo(SyncIssue.TYPE_JAR_DEPEND_ON_AAR);
        assertThat(syncIssue.getData()).isEqualTo("com.example:aar:1.0");
        assertThat(syncIssue.getMessage()).contains("aar artifact");
    }

    @Test
    public void traversesNativeAndroidProjectModel() {
        SimpleNativeFolder sourceFolder = new SimpleNativeFolder(
                file("src/main/cpp"), singletonStringMap("c++", "debug-cpp"));
        SimpleNativeFile sourceFile = new SimpleNativeFile(file("src/main/cpp/demo.cpp"), "debug-cpp");
        SimpleNativeArtifact artifact = new SimpleNativeArtifact(
                "libdemo", "clang", Collections.singletonList(sourceFolder), Collections.singletonList(sourceFile),
                file("build/intermediates/ndk/debug/lib/x86/libdemo.so"));
        SimpleNativeToolchain toolchain = new SimpleNativeToolchain(
                "clang", file("toolchains/clang"), file("toolchains/clang++"));
        SimpleNativeSettings settings = new SimpleNativeSettings("debug-cpp", Arrays.asList("-DDEBUG", "-std=c++11"));
        SimpleNativeAndroidProject project = new SimpleNativeAndroidProject(artifact, toolchain, settings);

        assertThat(project.getModelVersion()).isEqualTo(Version.ANDROID_GRADLE_PLUGIN_VERSION);
        assertThat(project.getApiVersion()).isEqualTo(Version.BUILDER_MODEL_API_VERSION);
        assertThat(project.getName()).isEqualTo("native-demo");
        assertThat(project.getBuildFiles()).containsExactly(file("CMakeLists.txt"));
        assertThat(project.getArtifacts()).containsExactly(artifact);
        assertThat(project.getToolChains()).containsExactly(toolchain);
        assertThat(project.getSettings()).containsExactly(settings);
        assertThat(project.getFileExtensions()).containsEntry("cpp", "c++");

        assertThat(artifact.getName()).isEqualTo("libdemo");
        assertThat(artifact.getToolChain()).isEqualTo("clang");
        assertThat(artifact.getSourceFolders()).containsExactly(sourceFolder);
        assertThat(artifact.getSourceFiles()).containsExactly(sourceFile);
        assertThat(artifact.getOutputFile()).isEqualTo(file("build/intermediates/ndk/debug/lib/x86/libdemo.so"));
        assertThat(sourceFolder.getFolderPath()).isEqualTo(file("src/main/cpp"));
        assertThat(sourceFolder.getPerLanguageSettings()).containsEntry("c++", "debug-cpp");
        assertThat(sourceFile.getFilePath()).isEqualTo(file("src/main/cpp/demo.cpp"));
        assertThat(sourceFile.getSettingsName()).isEqualTo("debug-cpp");
        assertThat(settings.getCompilerFlags()).containsExactly("-DDEBUG", "-std=c++11");
    }

    private static File file(String path) {
        return new File(PROJECT_DIR, path);
    }

    private static void assertBaseConfig(BaseConfig config, String expectedName) {
        assertThat(config.getName()).isEqualTo(expectedName);
        assertThat(config.getApplicationIdSuffix()).isEqualTo("." + expectedName);
        assertThat(config.getBuildConfigFields()).containsKey("APPLICATION_ID");
        assertThat(config.getResValues()).containsKey("APPLICATION_ID");
        assertThat(config.getProguardFiles()).containsExactly(file("proguard-rules.pro"));
        assertThat(config.getConsumerProguardFiles()).containsExactly(file("consumer-rules.pro"));
        assertThat(config.getTestProguardFiles()).containsExactly(file("test-rules.pro"));
        assertThat(config.getManifestPlaceholders()).containsEntry("hostName", "example.com");
        assertThat(config.getMultiDexEnabled()).isTrue();
        assertThat(config.getMultiDexKeepFile()).isEqualTo(file("multidex-keep.txt"));
        assertThat(config.getMultiDexKeepProguard()).isEqualTo(file("multidex-keep.pro"));
        assertThat(config.getJarJarRuleFiles()).containsExactly(file("jarjar-rules.txt"));
    }

    private static Set<String> linkedSet(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }

    private static Map<String, String> singletonStringMap(String key, String value) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(key, value);
        return map;
    }

    private static final class SimpleMavenCoordinates implements MavenCoordinates {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String packaging;
        private final String classifier;

        private SimpleMavenCoordinates(String groupId, String artifactId, String version, String packaging,
                String classifier) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.packaging = packaging;
            this.classifier = classifier;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getPackaging() {
            return packaging;
        }

        @Override
        public String getClassifier() {
            return classifier;
        }
    }

    private abstract static class SimpleLibrary implements Library {
        private final MavenCoordinates requestedCoordinates;
        private final MavenCoordinates resolvedCoordinates;

        private SimpleLibrary(MavenCoordinates requestedCoordinates, MavenCoordinates resolvedCoordinates) {
            this.requestedCoordinates = requestedCoordinates;
            this.resolvedCoordinates = resolvedCoordinates;
        }

        @Override
        public MavenCoordinates getRequestedCoordinates() {
            return requestedCoordinates;
        }

        @Override
        public MavenCoordinates getResolvedCoordinates() {
            return resolvedCoordinates;
        }
    }

    private static final class SimpleJavaLibrary extends SimpleLibrary implements JavaLibrary {
        private final File jarFile;
        private final List<? extends JavaLibrary> dependencies;
        private final boolean provided;

        private SimpleJavaLibrary(File jarFile, MavenCoordinates requestedCoordinates,
                MavenCoordinates resolvedCoordinates, List<? extends JavaLibrary> dependencies, boolean provided) {
            super(requestedCoordinates, resolvedCoordinates);
            this.jarFile = jarFile;
            this.dependencies = dependencies;
            this.provided = provided;
        }

        @Override
        public File getJarFile() {
            return jarFile;
        }

        @Override
        public List<? extends JavaLibrary> getDependencies() {
            return dependencies;
        }

        @Override
        public boolean isProvided() {
            return provided;
        }
    }

    private static final class SimpleAndroidLibrary extends SimpleLibrary implements AndroidLibrary {
        private final String project;
        private final String projectVariant;
        private final List<? extends AndroidLibrary> libraryDependencies;
        private final boolean optional;

        private SimpleAndroidLibrary(String project, String projectVariant, MavenCoordinates requestedCoordinates,
                MavenCoordinates resolvedCoordinates, List<? extends AndroidLibrary> libraryDependencies,
                boolean optional) {
            super(requestedCoordinates, resolvedCoordinates);
            this.project = project;
            this.projectVariant = projectVariant;
            this.libraryDependencies = libraryDependencies;
            this.optional = optional;
        }

        @Override
        public String getProject() {
            return project;
        }

        @Override
        public String getProjectVariant() {
            return projectVariant;
        }

        @Override
        public File getBundle() {
            return file("libs/library.aar");
        }

        @Override
        public File getFolder() {
            return file("exploded-aar/library");
        }

        @Override
        public List<? extends AndroidLibrary> getLibraryDependencies() {
            return libraryDependencies;
        }

        @Override
        public File getManifest() {
            return file("exploded-aar/library/AndroidManifest.xml");
        }

        @Override
        public File getJarFile() {
            return file("exploded-aar/library/classes.jar");
        }

        @Override
        public Collection<File> getLocalJars() {
            return Collections.singletonList(file("exploded-aar/library/libs/local.jar"));
        }

        @Override
        public File getResFolder() {
            return file("exploded-aar/library/res");
        }

        @Override
        public File getAssetsFolder() {
            return file("exploded-aar/library/assets");
        }

        @Override
        public File getJniFolder() {
            return file("exploded-aar/library/jni");
        }

        @Override
        public File getAidlFolder() {
            return file("exploded-aar/library/aidl");
        }

        @Override
        public File getRenderscriptFolder() {
            return file("exploded-aar/library/rs");
        }

        @Override
        public File getProguardRules() {
            return file("exploded-aar/library/proguard.txt");
        }

        @Override
        public File getLintJar() {
            return file("exploded-aar/library/lint.jar");
        }

        @Override
        public File getExternalAnnotations() {
            return file("exploded-aar/library/annotations.zip");
        }

        @Override
        public File getPublicResources() {
            return file("exploded-aar/library/public.txt");
        }

        @Override
        public boolean isOptional() {
            return optional;
        }
    }

    private static final class SimpleDependencies implements Dependencies {
        private final Collection<AndroidLibrary> libraries;
        private final Collection<JavaLibrary> javaLibraries;
        private final Collection<String> projects;

        private SimpleDependencies(Collection<AndroidLibrary> libraries, Collection<JavaLibrary> javaLibraries,
                Collection<String> projects) {
            this.libraries = libraries;
            this.javaLibraries = javaLibraries;
            this.projects = projects;
        }

        @Override
        public Collection<AndroidLibrary> getLibraries() {
            return libraries;
        }

        @Override
        public Collection<JavaLibrary> getJavaLibraries() {
            return javaLibraries;
        }

        @Override
        public Collection<String> getProjects() {
            return projects;
        }
    }

    private static final class SimpleClassField implements ClassField {
        private final String type;
        private final String name;
        private final String value;
        private final String documentation;

        private SimpleClassField(String type, String name, String value, String documentation) {
            this.type = type;
            this.name = name;
            this.value = value;
            this.documentation = documentation;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String getDocumentation() {
            return documentation;
        }

        @Override
        public Set<String> getAnnotations() {
            return linkedSet("Deprecated", "Generated");
        }
    }

    private abstract static class SimpleBaseConfig implements BaseConfig {
        private final String name;
        private final ClassField classField;

        private SimpleBaseConfig(String name, ClassField classField) {
            this.name = name;
            this.classField = classField;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getApplicationIdSuffix() {
            return "." + name;
        }

        @Override
        public Map<String, ClassField> getBuildConfigFields() {
            return singletonClassFieldMap(classField);
        }

        @Override
        public Map<String, ClassField> getResValues() {
            return singletonClassFieldMap(classField);
        }

        @Override
        public Collection<File> getProguardFiles() {
            return Collections.singletonList(file("proguard-rules.pro"));
        }

        @Override
        public Collection<File> getConsumerProguardFiles() {
            return Collections.singletonList(file("consumer-rules.pro"));
        }

        @Override
        public Collection<File> getTestProguardFiles() {
            return Collections.singletonList(file("test-rules.pro"));
        }

        @Override
        public Map<String, Object> getManifestPlaceholders() {
            Map<String, Object> placeholders = new LinkedHashMap<String, Object>();
            placeholders.put("hostName", "example.com");
            return placeholders;
        }

        @Override
        public Boolean getMultiDexEnabled() {
            return Boolean.TRUE;
        }

        @Override
        public File getMultiDexKeepFile() {
            return file("multidex-keep.txt");
        }

        @Override
        public File getMultiDexKeepProguard() {
            return file("multidex-keep.pro");
        }

        @Override
        public List<File> getJarJarRuleFiles() {
            return Collections.singletonList(file("jarjar-rules.txt"));
        }

        private static Map<String, ClassField> singletonClassFieldMap(ClassField classField) {
            Map<String, ClassField> fields = new LinkedHashMap<String, ClassField>();
            fields.put(classField.getName(), classField);
            return fields;
        }
    }

    private static final class SimpleBuildType extends SimpleBaseConfig implements BuildType {
        private final SigningConfig signingConfig;

        private SimpleBuildType(String name, SigningConfig signingConfig, ClassField classField) {
            super(name, classField);
            this.signingConfig = signingConfig;
        }

        @Override
        public boolean isDebuggable() {
            return true;
        }

        @Override
        public boolean isTestCoverageEnabled() {
            return true;
        }

        @Override
        public boolean isPseudoLocalesEnabled() {
            return false;
        }

        @Override
        public boolean isJniDebuggable() {
            return true;
        }

        @Override
        public boolean isRenderscriptDebuggable() {
            return true;
        }

        @Override
        public int getRenderscriptOptimLevel() {
            return 3;
        }

        @Override
        public String getVersionNameSuffix() {
            return "-debug";
        }

        @Override
        public boolean isMinifyEnabled() {
            return false;
        }

        @Override
        public boolean isZipAlignEnabled() {
            return true;
        }

        @Override
        public boolean isEmbedMicroApp() {
            return true;
        }

        @Override
        public SigningConfig getSigningConfig() {
            return signingConfig;
        }
    }

    private static final class SimpleProductFlavor extends SimpleBaseConfig implements ProductFlavor {
        private final SigningConfig signingConfig;

        private SimpleProductFlavor(String name, SigningConfig signingConfig, ClassField classField) {
            super(name, classField);
            this.signingConfig = signingConfig;
        }

        @Override
        public String getApplicationId() {
            return "com.example.demo";
        }

        @Override
        public Integer getVersionCode() {
            return 42;
        }

        @Override
        public String getVersionName() {
            return "1.0-demo";
        }

        @Override
        public ApiVersion getMinSdkVersion() {
            return new SimpleApiVersion(21, null);
        }

        @Override
        public ApiVersion getTargetSdkVersion() {
            return new SimpleApiVersion(23, "MNC");
        }

        @Override
        public Integer getMaxSdkVersion() {
            return 28;
        }

        @Override
        public Integer getRenderscriptTargetApi() {
            return 21;
        }

        @Override
        public Boolean getRenderscriptSupportModeEnabled() {
            return Boolean.TRUE;
        }

        @Override
        public Boolean getRenderscriptNdkModeEnabled() {
            return Boolean.FALSE;
        }

        @Override
        public String getTestApplicationId() {
            return "com.example.demo.test";
        }

        @Override
        public String getTestInstrumentationRunner() {
            return "android.support.test.runner.AndroidJUnitRunner";
        }

        @Override
        public Map<String, String> getTestInstrumentationRunnerArguments() {
            return singletonStringMap("clearPackageData", "true");
        }

        @Override
        public Boolean getTestHandleProfiling() {
            return Boolean.FALSE;
        }

        @Override
        public Boolean getTestFunctionalTest() {
            return Boolean.TRUE;
        }

        @Override
        public Collection<String> getResourceConfigurations() {
            return Arrays.asList("en", "xxhdpi");
        }

        @Override
        public SigningConfig getSigningConfig() {
            return signingConfig;
        }

        @Override
        public Set<String> getGeneratedDensities() {
            return linkedSet("mdpi", "xxhdpi");
        }

        @Override
        public String getDimension() {
            return "environment";
        }
    }

    private static final class SimpleApiVersion implements ApiVersion {
        private final int apiLevel;
        private final String codename;

        private SimpleApiVersion(int apiLevel, String codename) {
            this.apiLevel = apiLevel;
            this.codename = codename;
        }

        @Override
        public int getApiLevel() {
            return apiLevel;
        }

        @Override
        public String getCodename() {
            return codename;
        }

        @Override
        public String getApiString() {
            if (codename != null) {
                return codename;
            }
            return Integer.toString(apiLevel);
        }
    }

    private static final class SimpleSigningConfig implements SigningConfig {
        private final String name;
        private final File storeFile;
        private final String storePassword;
        private final String keyAlias;
        private final String keyPassword;
        private final String storeType;
        private final boolean signingReady;

        private SimpleSigningConfig(String name, File storeFile, String storePassword, String keyAlias,
                String keyPassword, String storeType, boolean signingReady) {
            this.name = name;
            this.storeFile = storeFile;
            this.storePassword = storePassword;
            this.keyAlias = keyAlias;
            this.keyPassword = keyPassword;
            this.storeType = storeType;
            this.signingReady = signingReady;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public File getStoreFile() {
            return storeFile;
        }

        @Override
        public String getStorePassword() {
            return storePassword;
        }

        @Override
        public String getKeyAlias() {
            return keyAlias;
        }

        @Override
        public String getKeyPassword() {
            return keyPassword;
        }

        @Override
        public String getStoreType() {
            return storeType;
        }

        @Override
        public boolean isSigningReady() {
            return signingReady;
        }
    }

    private static final class SimpleSourceProvider implements SourceProvider {
        private final String name;
        private final String basePath;

        private SimpleSourceProvider(String name, String basePath) {
            this.name = name;
            this.basePath = basePath;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public File getManifestFile() {
            return file(basePath + "/AndroidManifest.xml");
        }

        @Override
        public Collection<File> getJavaDirectories() {
            return Collections.singletonList(file(basePath + "/java"));
        }

        @Override
        public Collection<File> getResourcesDirectories() {
            return Collections.singletonList(file(basePath + "/resources"));
        }

        @Override
        public Collection<File> getAidlDirectories() {
            return Collections.singletonList(file(basePath + "/aidl"));
        }

        @Override
        public Collection<File> getRenderscriptDirectories() {
            return Collections.singletonList(file(basePath + "/rs"));
        }

        @Override
        public Collection<File> getCDirectories() {
            return Collections.singletonList(file(basePath + "/c"));
        }

        @Override
        public Collection<File> getCppDirectories() {
            return Collections.singletonList(file(basePath + "/cpp"));
        }

        @Override
        public Collection<File> getResDirectories() {
            return Collections.singletonList(file(basePath + "/res"));
        }

        @Override
        public Collection<File> getAssetsDirectories() {
            return Collections.singletonList(file(basePath + "/assets"));
        }

        @Override
        public Collection<File> getJniLibsDirectories() {
            return Collections.singletonList(file(basePath + "/jniLibs"));
        }
    }

    private static final class SimpleSourceProviderContainer implements SourceProviderContainer {
        private final String artifactName;
        private final SourceProvider sourceProvider;

        private SimpleSourceProviderContainer(String artifactName, SourceProvider sourceProvider) {
            this.artifactName = artifactName;
            this.sourceProvider = sourceProvider;
        }

        @Override
        public String getArtifactName() {
            return artifactName;
        }

        @Override
        public SourceProvider getSourceProvider() {
            return sourceProvider;
        }
    }

    private static final class SimpleBuildTypeContainer implements BuildTypeContainer {
        private final BuildType buildType;
        private final SourceProvider sourceProvider;
        private final Collection<SourceProviderContainer> extraSourceProviders;

        private SimpleBuildTypeContainer(BuildType buildType, SourceProvider sourceProvider,
                SourceProviderContainer extraSourceProvider) {
            this.buildType = buildType;
            this.sourceProvider = sourceProvider;
            this.extraSourceProviders = Collections.singletonList(extraSourceProvider);
        }

        @Override
        public BuildType getBuildType() {
            return buildType;
        }

        @Override
        public SourceProvider getSourceProvider() {
            return sourceProvider;
        }

        @Override
        public Collection<SourceProviderContainer> getExtraSourceProviders() {
            return extraSourceProviders;
        }
    }

    private static final class SimpleProductFlavorContainer implements ProductFlavorContainer {
        private final ProductFlavor productFlavor;
        private final SourceProvider sourceProvider;
        private final Collection<SourceProviderContainer> extraSourceProviders;

        private SimpleProductFlavorContainer(ProductFlavor productFlavor, SourceProvider sourceProvider,
                SourceProviderContainer extraSourceProvider) {
            this.productFlavor = productFlavor;
            this.sourceProvider = sourceProvider;
            this.extraSourceProviders = Collections.singletonList(extraSourceProvider);
        }

        @Override
        public ProductFlavor getProductFlavor() {
            return productFlavor;
        }

        @Override
        public SourceProvider getSourceProvider() {
            return sourceProvider;
        }

        @Override
        public Collection<SourceProviderContainer> getExtraSourceProviders() {
            return extraSourceProviders;
        }
    }

    private abstract static class SimpleBaseArtifact {
        private final String name;
        private final Dependencies dependencies;
        private final SourceProvider variantSourceProvider;
        private final SourceProvider multiFlavorSourceProvider;

        private SimpleBaseArtifact(String name, Dependencies dependencies, SourceProvider variantSourceProvider,
                SourceProvider multiFlavorSourceProvider) {
            this.name = name;
            this.dependencies = dependencies;
            this.variantSourceProvider = variantSourceProvider;
            this.multiFlavorSourceProvider = multiFlavorSourceProvider;
        }

        public String getName() {
            return name;
        }

        public String getCompileTaskName() {
            return "compile" + name + "JavaWithJavac";
        }

        public String getAssembleTaskName() {
            return "assemble" + name;
        }

        public File getClassesFolder() {
            return file("build/intermediates/classes/" + name);
        }

        public File getJavaResourcesFolder() {
            return file("build/intermediates/javaResources/" + name);
        }

        public Dependencies getDependencies() {
            return dependencies;
        }

        public SourceProvider getVariantSourceProvider() {
            return variantSourceProvider;
        }

        public SourceProvider getMultiFlavorSourceProvider() {
            return multiFlavorSourceProvider;
        }

        public Set<String> getIdeSetupTaskNames() {
            return linkedSet("generate" + name + "Sources");
        }

        public Collection<File> getGeneratedSourceFolders() {
            return Collections.singletonList(file("build/generated/source/r/" + name));
        }
    }

    private static final class SimpleAndroidArtifact extends SimpleBaseArtifact implements AndroidArtifact {
        private final Collection<AndroidArtifactOutput> outputs;
        private final Set<String> abiFilters;
        private final NativeLibrary nativeLibrary;
        private final ClassField classField;

        private SimpleAndroidArtifact(String name, Dependencies dependencies, SourceProvider variantSourceProvider,
                SourceProvider multiFlavorSourceProvider, Collection<AndroidArtifactOutput> outputs,
                Set<String> abiFilters, NativeLibrary nativeLibrary, ClassField classField) {
            super(name, dependencies, variantSourceProvider, multiFlavorSourceProvider);
            this.outputs = outputs;
            this.abiFilters = abiFilters;
            this.nativeLibrary = nativeLibrary;
            this.classField = classField;
        }

        @Override
        public Collection<AndroidArtifactOutput> getOutputs() {
            return outputs;
        }

        @Override
        public boolean isSigned() {
            return true;
        }

        @Override
        public String getSigningConfigName() {
            return "debug";
        }

        @Override
        public String getApplicationId() {
            return "com.example.demo";
        }

        @Override
        public String getSourceGenTaskName() {
            return "generate" + getName() + "Sources";
        }

        @Override
        public Collection<File> getGeneratedResourceFolders() {
            return Collections.singletonList(file("build/generated/res/resValues/" + getName()));
        }

        @Override
        public Set<String> getAbiFilters() {
            return abiFilters;
        }

        @Override
        public Collection<NativeLibrary> getNativeLibraries() {
            return Collections.singletonList(nativeLibrary);
        }

        @Override
        public Map<String, ClassField> getBuildConfigFields() {
            return singletonClassFieldMap();
        }

        @Override
        public Map<String, ClassField> getResValues() {
            return singletonClassFieldMap();
        }

        private Map<String, ClassField> singletonClassFieldMap() {
            Map<String, ClassField> fields = new LinkedHashMap<String, ClassField>();
            fields.put(classField.getName(), classField);
            return fields;
        }
    }

    private static final class SimpleJavaArtifact extends SimpleBaseArtifact implements JavaArtifact {
        private final File mockablePlatformJar;

        private SimpleJavaArtifact(String name, Dependencies dependencies, SourceProvider variantSourceProvider,
                SourceProvider multiFlavorSourceProvider, File mockablePlatformJar) {
            super(name, dependencies, variantSourceProvider, multiFlavorSourceProvider);
            this.mockablePlatformJar = mockablePlatformJar;
        }

        @Override
        public File getMockablePlatformJar() {
            return mockablePlatformJar;
        }
    }

    private static final class SimpleVariant implements Variant {
        private final String name;
        private final String displayName;
        private final String buildType;
        private final List<String> productFlavors;
        private final ProductFlavor mergedFlavor;
        private final AndroidArtifact mainArtifact;
        private final JavaArtifact javaArtifact;

        private SimpleVariant(String name, String displayName, String buildType, List<String> productFlavors,
                ProductFlavor mergedFlavor, AndroidArtifact mainArtifact, JavaArtifact javaArtifact) {
            this.name = name;
            this.displayName = displayName;
            this.buildType = buildType;
            this.productFlavors = productFlavors;
            this.mergedFlavor = mergedFlavor;
            this.mainArtifact = mainArtifact;
            this.javaArtifact = javaArtifact;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public AndroidArtifact getMainArtifact() {
            return mainArtifact;
        }

        @Override
        public Collection<AndroidArtifact> getExtraAndroidArtifacts() {
            return Collections.emptyList();
        }

        @Override
        public Collection<JavaArtifact> getExtraJavaArtifacts() {
            return Collections.singletonList(javaArtifact);
        }

        @Override
        public String getBuildType() {
            return buildType;
        }

        @Override
        public List<String> getProductFlavors() {
            return productFlavors;
        }

        @Override
        public ProductFlavor getMergedFlavor() {
            return mergedFlavor;
        }
    }

    private static final class SimpleFilterData implements FilterData {
        private final String filterType;
        private final String identifier;

        private SimpleFilterData(String filterType, String identifier) {
            this.filterType = filterType;
            this.identifier = identifier;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String getFilterType() {
            return filterType;
        }
    }

    private static final class SimpleOutputFile implements OutputFile {
        private final String outputType;
        private final File outputFile;
        private final Collection<FilterData> filters;

        private SimpleOutputFile(String outputType, File outputFile, Collection<FilterData> filters) {
            this.outputType = outputType;
            this.outputFile = outputFile;
            this.filters = filters;
        }

        @Override
        public String getOutputType() {
            return outputType;
        }

        @Override
        public Collection<String> getFilterTypes() {
            Set<String> filterTypes = new LinkedHashSet<String>();
            for (FilterData filter : filters) {
                filterTypes.add(filter.getFilterType());
            }
            return filterTypes;
        }

        @Override
        public Collection<FilterData> getFilters() {
            return filters;
        }

        @Override
        public File getOutputFile() {
            return outputFile;
        }
    }

    private static final class SimpleVariantOutput implements VariantOutput {
        private final OutputFile mainOutputFile;
        private final Collection<? extends OutputFile> outputs;
        private final File splitFolder;
        private final int versionCode;

        private SimpleVariantOutput(OutputFile mainOutputFile, Collection<? extends OutputFile> outputs,
                File splitFolder, int versionCode) {
            this.mainOutputFile = mainOutputFile;
            this.outputs = outputs;
            this.splitFolder = splitFolder;
            this.versionCode = versionCode;
        }

        @Override
        public OutputFile getMainOutputFile() {
            return mainOutputFile;
        }

        @Override
        public Collection<? extends OutputFile> getOutputs() {
            return outputs;
        }

        @Override
        public File getSplitFolder() {
            return splitFolder;
        }

        @Override
        public int getVersionCode() {
            return versionCode;
        }
    }

    private static final class SimpleAndroidArtifactOutput implements AndroidArtifactOutput {
        private final OutputFile mainOutputFile;
        private final Collection<? extends OutputFile> outputs;
        private final File splitFolder;
        private final int versionCode;
        private final String assembleTaskName;
        private final File generatedManifest;

        private SimpleAndroidArtifactOutput(OutputFile mainOutputFile, Collection<? extends OutputFile> outputs,
                File splitFolder, int versionCode, String assembleTaskName, File generatedManifest) {
            this.mainOutputFile = mainOutputFile;
            this.outputs = outputs;
            this.splitFolder = splitFolder;
            this.versionCode = versionCode;
            this.assembleTaskName = assembleTaskName;
            this.generatedManifest = generatedManifest;
        }

        @Override
        public OutputFile getMainOutputFile() {
            return mainOutputFile;
        }

        @Override
        public Collection<? extends OutputFile> getOutputs() {
            return outputs;
        }

        @Override
        public File getSplitFolder() {
            return splitFolder;
        }

        @Override
        public int getVersionCode() {
            return versionCode;
        }

        @Override
        public String getAssembleTaskName() {
            return assembleTaskName;
        }

        @Override
        public File getGeneratedManifest() {
            return generatedManifest;
        }
    }

    private static final class SimpleNativeLibrary implements NativeLibrary {
        private final String name;
        private final String abi;
        private final String toolchainName;

        private SimpleNativeLibrary(String name, String abi, String toolchainName) {
            this.name = name;
            this.abi = abi;
            this.toolchainName = toolchainName;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getAbi() {
            return abi;
        }

        @Override
        public String getToolchainName() {
            return toolchainName;
        }

        @Override
        public List<File> getCIncludeDirs() {
            return Collections.singletonList(file("src/main/c/include"));
        }

        @Override
        public List<File> getCppIncludeDirs() {
            return Collections.singletonList(file("src/main/cpp/include"));
        }

        @Override
        public List<File> getCSystemIncludeDirs() {
            return Collections.singletonList(file("ndk/sysroot/usr/include"));
        }

        @Override
        public List<File> getCppSystemIncludeDirs() {
            return Collections.singletonList(file("ndk/sources/cxx-stl/gnu-libstdc++/include"));
        }

        @Override
        public List<String> getCDefines() {
            return Collections.singletonList("ANDROID");
        }

        @Override
        public List<String> getCppDefines() {
            return Collections.singletonList("ANDROID_CPP");
        }

        @Override
        public List<String> getCCompilerFlags() {
            return Collections.singletonList("-Wall");
        }

        @Override
        public List<String> getCppCompilerFlags() {
            return Collections.singletonList("-std=c++11");
        }

        @Override
        public List<File> getDebuggableLibraryFolders() {
            return Collections.singletonList(file("build/intermediates/ndk/debug/lib/x86"));
        }
    }

    private static final class SimpleArtifactMetaData implements ArtifactMetaData {
        private final String name;
        private final boolean test;
        private final int type;

        private SimpleArtifactMetaData(String name, boolean test, int type) {
            this.name = name;
            this.test = test;
            this.type = type;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isTest() {
            return test;
        }

        @Override
        public int getType() {
            return type;
        }
    }

    private static final class SimpleAndroidProject implements AndroidProject {
        private final ProductFlavorContainer defaultConfig;
        private final BuildTypeContainer buildTypeContainer;
        private final Variant variant;
        private final ArtifactMetaData extraArtifact;
        private final NativeToolchain toolchain;
        private final SigningConfig signingConfig;
        private final String name;

        private SimpleAndroidProject(String name, ProductFlavorContainer defaultConfig,
                BuildTypeContainer buildTypeContainer, Variant variant, ArtifactMetaData extraArtifact,
                NativeToolchain toolchain, SigningConfig signingConfig) {
            this.name = name;
            this.defaultConfig = defaultConfig;
            this.buildTypeContainer = buildTypeContainer;
            this.variant = variant;
            this.extraArtifact = extraArtifact;
            this.toolchain = toolchain;
            this.signingConfig = signingConfig;
        }

        @Override
        public String getModelVersion() {
            return Version.ANDROID_GRADLE_PLUGIN_VERSION;
        }

        @Override
        public int getApiVersion() {
            return Version.BUILDER_MODEL_API_VERSION;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isLibrary() {
            return false;
        }

        @Override
        public ProductFlavorContainer getDefaultConfig() {
            return defaultConfig;
        }

        @Override
        public Collection<BuildTypeContainer> getBuildTypes() {
            return Collections.singletonList(buildTypeContainer);
        }

        @Override
        public Collection<ProductFlavorContainer> getProductFlavors() {
            return Collections.singletonList(defaultConfig);
        }

        @Override
        public Collection<Variant> getVariants() {
            return Collections.singletonList(variant);
        }

        @Override
        public Collection<String> getFlavorDimensions() {
            return Collections.singletonList("environment");
        }

        @Override
        public Collection<ArtifactMetaData> getExtraArtifacts() {
            return Collections.singletonList(extraArtifact);
        }

        @Override
        public String getCompileTarget() {
            return "android-23";
        }

        @Override
        public Collection<String> getBootClasspath() {
            return Arrays.asList("android.jar", "optional/org.apache.http.legacy.jar");
        }

        @Override
        public Collection<File> getFrameworkSources() {
            return Collections.singletonList(file("frameworks/base/core/java"));
        }

        @Override
        public Collection<NativeToolchain> getNativeToolchains() {
            return Collections.singletonList(toolchain);
        }

        @Override
        public Collection<SigningConfig> getSigningConfigs() {
            return Collections.singletonList(signingConfig);
        }

        @Override
        public AaptOptions getAaptOptions() {
            return new SimpleAaptOptions();
        }

        @Override
        public LintOptions getLintOptions() {
            return new SimpleLintOptions();
        }

        @Override
        @SuppressWarnings("deprecation")
        public Collection<String> getUnresolvedDependencies() {
            return Collections.singletonList("com.missing:artifact:1.0");
        }

        @Override
        public Collection<SyncIssue> getSyncIssues() {
            return Collections.<SyncIssue>singletonList(new SimpleSyncIssue(SyncIssue.SEVERITY_ERROR,
                    SyncIssue.TYPE_UNRESOLVED_DEPENDENCY, "com.missing:artifact:1.0", "Could not resolve dependency"));
        }

        @Override
        public JavaCompileOptions getJavaCompileOptions() {
            return new SimpleJavaCompileOptions();
        }

        @Override
        public File getBuildFolder() {
            return file("build");
        }

        @Override
        public String getResourcePrefix() {
            return "demo_";
        }
    }

    private static final class SimpleAaptOptions implements AaptOptions {
        @Override
        public String getIgnoreAssets() {
            return "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~";
        }

        @Override
        public Collection<String> getNoCompress() {
            return Arrays.asList("webp", "mp3");
        }

        @Override
        public boolean getFailOnMissingConfigEntry() {
            return true;
        }

        @Override
        public List<String> getAdditionalParameters() {
            return Arrays.asList("--auto-add-overlay", "--no-version-vectors");
        }
    }

    private static final class SimpleAdbOptions implements AdbOptions {
        @Override
        public int getTimeOutInMs() {
            return 30_000;
        }

        @Override
        public Collection<String> getInstallOptions() {
            return Arrays.asList("-r", "-d");
        }
    }

    private static final class SimpleDataBindingOptions implements DataBindingOptions {
        @Override
        public String getVersion() {
            return "1.0-rc4";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean getAddDefaultAdapters() {
            return false;
        }
    }

    private static final class SimplePackagingOptions implements PackagingOptions {
        @Override
        public Set<String> getExcludes() {
            return linkedSet("META-INF/LICENSE");
        }

        @Override
        public Set<String> getPickFirsts() {
            return linkedSet("lib/x86/libshared.so");
        }

        @Override
        public Set<String> getMerges() {
            return linkedSet("META-INF/services/javax.annotation.processing.Processor");
        }
    }

    private static final class SimpleJavaCompileOptions implements JavaCompileOptions {
        @Override
        public String getEncoding() {
            return "UTF-8";
        }

        @Override
        public String getSourceCompatibility() {
            return "1.7";
        }

        @Override
        public String getTargetCompatibility() {
            return "1.7";
        }
    }

    private static final class SimpleLintOptions implements LintOptions {
        @Override
        public Set<String> getDisable() {
            return linkedSet("TypographyFractions");
        }

        @Override
        public Set<String> getEnable() {
            return linkedSet("NewApi");
        }

        @Override
        public Set<String> getCheck() {
            return linkedSet("UnusedResources");
        }

        @Override
        public boolean isAbortOnError() {
            return true;
        }

        @Override
        public boolean isAbsolutePaths() {
            return false;
        }

        @Override
        public boolean isNoLines() {
            return true;
        }

        @Override
        public boolean isQuiet() {
            return false;
        }

        @Override
        public boolean isCheckAllWarnings() {
            return true;
        }

        @Override
        public boolean isIgnoreWarnings() {
            return false;
        }

        @Override
        public boolean isWarningsAsErrors() {
            return true;
        }

        @Override
        public boolean isExplainIssues() {
            return true;
        }

        @Override
        public boolean isShowAll() {
            return false;
        }

        @Override
        public File getLintConfig() {
            return file("lint.xml");
        }

        @Override
        public boolean getTextReport() {
            return true;
        }

        @Override
        public File getTextOutput() {
            return file("reports/lint-results.txt");
        }

        @Override
        public boolean getHtmlReport() {
            return true;
        }

        @Override
        public File getHtmlOutput() {
            return file("reports/lint-results.html");
        }

        @Override
        public boolean getXmlReport() {
            return true;
        }

        @Override
        public File getXmlOutput() {
            return file("reports/lint-results.xml");
        }

        @Override
        public boolean isCheckReleaseBuilds() {
            return true;
        }

        @Override
        public Map<String, Integer> getSeverityOverrides() {
            Map<String, Integer> overrides = new LinkedHashMap<String, Integer>();
            overrides.put("NewApi", LintOptions.SEVERITY_ERROR);
            return overrides;
        }
    }

    private static final class SimpleSyncIssue implements SyncIssue {
        private final int severity;
        private final int type;
        private final String data;
        private final String message;

        private SimpleSyncIssue(int severity, int type, String data, String message) {
            this.severity = severity;
            this.type = type;
            this.data = data;
            this.message = message;
        }

        @Override
        public int getSeverity() {
            return severity;
        }

        @Override
        public int getType() {
            return type;
        }

        @Override
        public String getData() {
            return data;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    private static final class SimpleNativeToolchain implements NativeToolchain {
        private final String name;
        private final File cCompilerExecutable;
        private final File cppCompilerExecutable;

        private SimpleNativeToolchain(String name, File cCompilerExecutable, File cppCompilerExecutable) {
            this.name = name;
            this.cCompilerExecutable = cCompilerExecutable;
            this.cppCompilerExecutable = cppCompilerExecutable;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public File getCCompilerExecutable() {
            return cCompilerExecutable;
        }

        @Override
        public File getCppCompilerExecutable() {
            return cppCompilerExecutable;
        }
    }

    private static final class SimpleNativeFolder implements NativeFolder {
        private final File folderPath;
        private final Map<String, String> perLanguageSettings;

        private SimpleNativeFolder(File folderPath, Map<String, String> perLanguageSettings) {
            this.folderPath = folderPath;
            this.perLanguageSettings = perLanguageSettings;
        }

        @Override
        public File getFolderPath() {
            return folderPath;
        }

        @Override
        public Map<String, String> getPerLanguageSettings() {
            return perLanguageSettings;
        }
    }

    private static final class SimpleNativeFile implements NativeFile {
        private final File filePath;
        private final String settingsName;

        private SimpleNativeFile(File filePath, String settingsName) {
            this.filePath = filePath;
            this.settingsName = settingsName;
        }

        @Override
        public File getFilePath() {
            return filePath;
        }

        @Override
        public String getSettingsName() {
            return settingsName;
        }
    }

    private static final class SimpleNativeArtifact implements NativeArtifact {
        private final String name;
        private final String toolChain;
        private final Collection<NativeFolder> sourceFolders;
        private final Collection<NativeFile> sourceFiles;
        private final File outputFile;

        private SimpleNativeArtifact(String name, String toolChain, Collection<NativeFolder> sourceFolders,
                Collection<NativeFile> sourceFiles, File outputFile) {
            this.name = name;
            this.toolChain = toolChain;
            this.sourceFolders = sourceFolders;
            this.sourceFiles = sourceFiles;
            this.outputFile = outputFile;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getToolChain() {
            return toolChain;
        }

        @Override
        public Collection<NativeFolder> getSourceFolders() {
            return sourceFolders;
        }

        @Override
        public Collection<NativeFile> getSourceFiles() {
            return sourceFiles;
        }

        @Override
        public File getOutputFile() {
            return outputFile;
        }
    }

    private static final class SimpleNativeSettings implements NativeSettings {
        private final String name;
        private final List<String> compilerFlags;

        private SimpleNativeSettings(String name, List<String> compilerFlags) {
            this.name = name;
            this.compilerFlags = compilerFlags;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getCompilerFlags() {
            return compilerFlags;
        }
    }

    private static final class SimpleNativeAndroidProject implements NativeAndroidProject {
        private final NativeArtifact artifact;
        private final NativeToolchain toolchain;
        private final NativeSettings settings;

        private SimpleNativeAndroidProject(NativeArtifact artifact, NativeToolchain toolchain,
                NativeSettings settings) {
            this.artifact = artifact;
            this.toolchain = toolchain;
            this.settings = settings;
        }

        @Override
        public String getModelVersion() {
            return Version.ANDROID_GRADLE_PLUGIN_VERSION;
        }

        @Override
        public int getApiVersion() {
            return Version.BUILDER_MODEL_API_VERSION;
        }

        @Override
        public String getName() {
            return "native-demo";
        }

        @Override
        public Collection<File> getBuildFiles() {
            return Collections.singletonList(file("CMakeLists.txt"));
        }

        @Override
        public Collection<NativeArtifact> getArtifacts() {
            return Collections.singletonList(artifact);
        }

        @Override
        public Collection<NativeToolchain> getToolChains() {
            return Collections.singletonList(toolchain);
        }

        @Override
        public Collection<NativeSettings> getSettings() {
            return Collections.singletonList(settings);
        }

        @Override
        public Map<String, String> getFileExtensions() {
            return singletonStringMap("cpp", "c++");
        }
    }
}
