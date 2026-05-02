/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_sbt.compiler_interface

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xsbti.Position
import xsbti.Problem
import xsbti.Reporter
import xsbti.Severity
import xsbti.T2
import xsbti.UseScope
import xsbti.api.{Package => ApiPackage, _}
import xsbti.compile._
import xsbti.compile.analysis._

import java.io.File
import java.lang.{Boolean => JBoolean, Integer => JInteger, Long => JLong}
import java.util.EnumSet
import java.util.Optional
import java.util.{ArrayList, HashSet, LinkedHashMap, Map => JMap, Set => JSet}
import java.util.function.Function
import java.util.function.Supplier

final class Compiler_interfaceTest {
  @Test
  def apiEnumsExposeStableDeclarationOrderAndLookup(): Unit = {
    assertThat(DefinitionType.values()).containsExactly(
      DefinitionType.Trait,
      DefinitionType.ClassDef,
      DefinitionType.Module,
      DefinitionType.PackageModule
    )
    assertThat(Variance.values()).containsExactly(Variance.Contravariant, Variance.Covariant, Variance.Invariant)
    assertThat(ParameterModifier.values()).containsExactly(
      ParameterModifier.Repeated,
      ParameterModifier.Plain,
      ParameterModifier.ByName
    )
    assertThat(DependencyContext.values()).containsExactly(
      DependencyContext.DependencyByMemberRef,
      DependencyContext.DependencyByInheritance,
      DependencyContext.LocalDependencyByInheritance
    )
    assertThat(UseScope.values()).containsExactly(UseScope.Default, UseScope.Implicit, UseScope.PatMatTarget)
    assertThat(CompileOrder.values()).containsExactly(
      CompileOrder.Mixed,
      CompileOrder.JavaThenScala,
      CompileOrder.ScalaThenJava
    )

    assertThat(DefinitionType.valueOf("Trait")).isSameAs(DefinitionType.Trait)
    assertThat(Variance.valueOf("Covariant")).isSameAs(Variance.Covariant)
    assertThat(ParameterModifier.valueOf("ByName")).isSameAs(ParameterModifier.ByName)
    assertThat(DependencyContext.valueOf("LocalDependencyByInheritance"))
      .isSameAs(DependencyContext.LocalDependencyByInheritance)
    assertThat(UseScope.valueOf("PatMatTarget")).isSameAs(UseScope.PatMatTarget)
    assertThat(CompileOrder.valueOf("ScalaThenJava")).isSameAs(CompileOrder.ScalaThenJava)
  }

  @Test
  def accessModifiersQualifiersAndPathsKeepValueSemantics(): Unit = {
    val publicAccess = Public.of()
    val privateQualifier = IdQualifier.of("example.internal")
    val privateAccess = Private.of(privateQualifier)
    val protectedAccess = Protected.of(ThisQualifier.of())
    val path = Path.of(Array[PathComponent](This.of(), Id.of("Service"), Id.of("instance")))
    val singleton = Singleton.of(path)
    val renamedPath = path.withComponents(Array[PathComponent](Id.of("example"), Id.of("Service")))

    assertThat(publicAccess).isEqualTo(Public.create())
    assertThat(publicAccess.hashCode()).isEqualTo(Public.create().hashCode())
    assertThat(privateAccess.qualifier()).isEqualTo(privateQualifier)
    assertThat(privateAccess.withQualifier(IdQualifier.of("example.api")).qualifier())
      .isEqualTo(IdQualifier.of("example.api"))
    assertThat(protectedAccess.qualifier()).isEqualTo(ThisQualifier.of())
    assertThat(path.components()).containsExactly(This.of(), Id.of("Service"), Id.of("instance"))
    assertThat(singleton.path()).isSameAs(path)
    assertThat(renamedPath.components()).containsExactly(Id.of("example"), Id.of("Service"))
    assertThat(ApiPackage.of("example.compiler").withName("example.compiler.api").name())
      .isEqualTo("example.compiler.api")
  }

  @Test
  def typesAnnotationsDefinitionsAndWithersModelScalaApiShapes(): Unit = {
    val stringType = Projection.of(Singleton.of(Path.of(Array[PathComponent](Id.of("scala")))), "String")
    val intType = Projection.of(Singleton.of(Path.of(Array[PathComponent](Id.of("scala")))), "Int")
    val listOfString = Parameterized.of(
      Projection.of(Singleton.of(Path.of(Array[PathComponent](Id.of("scala"), Id.of("collection"), Id.of("immutable")))), "List"),
      Array[Type](stringType)
    )
    val constantAnswer = Constant.of(intType, "42")
    val annotationArgument = AnnotationArgument.of("since", "1.0")
    val annotation = Annotation.of(
      Projection.of(Singleton.of(Path.of(Array[PathComponent](Id.of("scala")))), "deprecated"),
      Array(annotationArgument)
    )
    val typeParameter = TypeParameter.of(
      "A",
      Array(annotation),
      Array.empty[TypeParameter],
      Variance.Covariant,
      EmptyType.of(),
      Projection.of(Singleton.of(Path.of(Array[PathComponent](Id.of("scala")))), "AnyRef")
    )
    val parameter = MethodParameter.of("values", listOfString, true, ParameterModifier.Repeated)
    val parameterList = ParameterList.of(Array(parameter), false)
    val method = Def.of(
      "total",
      Public.of(),
      noModifiers,
      Array(annotation),
      Array(typeParameter),
      Array(parameterList),
      constantAnswer
    )
    val value = Val.of("cached", Public.of(), noModifiers.withLazyFlag, Array.empty[Annotation], listOfString)
    val variable = Var.of("counter", Private.of(IdQualifier.of("state")), noModifiers, Array.empty[Annotation], intType)
    val alias = TypeAlias.of("Names", Public.of(), noModifiers, Array.empty[Annotation], Array.empty[TypeParameter], listOfString)
    val declaration = TypeDeclaration.of(
      "Element",
      Public.of(),
      noModifiers,
      Array.empty[Annotation],
      Array.empty[TypeParameter],
      EmptyType.of(),
      stringType
    )

    assertThat(listOfString.baseType()).isEqualTo(
      Projection.of(Singleton.of(Path.of(Array[PathComponent](Id.of("scala"), Id.of("collection"), Id.of("immutable")))), "List")
    )
    assertThat(listOfString.typeArguments()).containsExactly(stringType)
    assertThat(listOfString.withTypeArguments(Array[Type](intType)).typeArguments()).containsExactly(intType)
    assertThat(constantAnswer.baseType()).isEqualTo(intType)
    assertThat(constantAnswer.value()).isEqualTo("42")
    assertThat(annotation.arguments()).containsExactly(annotationArgument)
    assertThat(annotationArgument.withValue("2.0").value()).isEqualTo("2.0")
    assertThat(typeParameter.id()).isEqualTo("A")
    assertThat(typeParameter.variance()).isSameAs(Variance.Covariant)
    assertThat(typeParameter.withVariance(Variance.Invariant).variance()).isSameAs(Variance.Invariant)
    assertThat(parameter.name()).isEqualTo("values")
    assertThat(parameter.hasDefault()).isTrue()
    assertThat(parameter.modifier()).isSameAs(ParameterModifier.Repeated)
    assertThat(parameter.withModifier(ParameterModifier.ByName).modifier()).isSameAs(ParameterModifier.ByName)
    assertThat(parameterList.parameters()).containsExactly(parameter)
    assertThat(parameterList.isImplicit()).isFalse()
    assertThat(parameterList.withIsImplicit(true).isImplicit()).isTrue()

    assertThat(method.name()).isEqualTo("total")
    assertThat(method.valueParameters()).containsExactly(parameterList)
    assertThat(method.returnType()).isEqualTo(constantAnswer)
    assertThat(method.withReturnType(intType).returnType()).isEqualTo(intType)
    assertThat(method.withName("sum").name()).isEqualTo("sum")
    assertThat(value.name()).isEqualTo("cached")
    assertThat(value.tpe()).isEqualTo(listOfString)
    assertThat(value.modifiers().isLazy()).isTrue()
    assertThat(value.withTpe(stringType).tpe()).isEqualTo(stringType)
    assertThat(variable.access()).isEqualTo(Private.of(IdQualifier.of("state")))
    assertThat(variable.withName("updatedCounter").name()).isEqualTo("updatedCounter")
    assertThat(alias.tpe()).isEqualTo(listOfString)
    assertThat(alias.withTpe(stringType).tpe()).isEqualTo(stringType)
    assertThat(declaration.lowerBound()).isEqualTo(EmptyType.of())
    assertThat(declaration.upperBound()).isEqualTo(stringType)
    assertThat(declaration.withLowerBound(intType).lowerBound()).isEqualTo(intType)
  }

  @Test
  def structuresClassApisCompanionsAndAnalyzedClassesResolveLazyFieldsOnce(): Unit = {
    var parentEvaluations = 0
    var declarationEvaluations = 0
    var companionEvaluations = 0
    val parentType = Projection.of(Singleton.of(Path.of(Array[PathComponent](Id.of("scala")))), "AnyRef")
    val member = Val.of("name", Public.of(), noModifiers, Array.empty[Annotation], parentType)
    val structure = Structure.of(
      SafeLazy.apply(new Supplier[Array[Type]] {
        override def get(): Array[Type] = {
          parentEvaluations += 1
          Array(parentType)
        }
      }),
      SafeLazy.apply(new Supplier[Array[ClassDefinition]] {
        override def get(): Array[ClassDefinition] = {
          declarationEvaluations += 1
          Array(member)
        }
      }),
      SafeLazy.strict(Array.empty[ClassDefinition])
    )
    val classApi = ClassLike.of(
      "example.Service",
      Public.of(),
      noModifiers,
      Array.empty[Annotation],
      DefinitionType.ClassDef,
      SafeLazy.strict(EmptyType.of()),
      SafeLazy.strict(structure),
      Array("java.lang.Deprecated"),
      Array(parentType),
      true,
      Array.empty[TypeParameter]
    )
    val objectApi = classApi.withDefinitionType(DefinitionType.Module).withName("example.Service$").withTopLevel(true)
    val companions = Companions.of(classApi, objectApi)
    val nameHash = NameHash.of("name", UseScope.Default, 12345)
    val analyzed = AnalyzedClass.of(
      123456789L,
      "example.Service",
      SafeLazy.apply(new Supplier[Companions] {
        override def get(): Companions = {
          companionEvaluations += 1
          companions
        }
      }),
      37,
      Array(nameHash),
      false,
      99
    )

    assertThat(structure.parents()).containsExactly(parentType)
    assertThat(structure.parents()).containsExactly(parentType)
    assertThat(parentEvaluations).isEqualTo(1)
    assertThat(structure.declared()).containsExactly(member)
    assertThat(structure.declared()).containsExactly(member)
    assertThat(declarationEvaluations).isEqualTo(1)
    assertThat(structure.inherited()).isEmpty()
    assertThat(structure.withParents(SafeLazy.strict(Array[Type](EmptyType.of()))).parents()).containsExactly(EmptyType.of())

    assertThat(classApi.name()).isEqualTo("example.Service")
    assertThat(classApi.definitionType()).isSameAs(DefinitionType.ClassDef)
    assertThat(classApi.selfType()).isEqualTo(EmptyType.of())
    assertThat(classApi.structure()).isSameAs(structure)
    assertThat(classApi.savedAnnotations()).containsExactly("java.lang.Deprecated")
    assertThat(classApi.childrenOfSealedClass()).containsExactly(parentType)
    assertThat(classApi.topLevel()).isTrue()
    assertThat(classApi.withAccess(Private.of(IdQualifier.of("example"))).access())
      .isEqualTo(Private.of(IdQualifier.of("example")))
    assertThat(objectApi.definitionType()).isSameAs(DefinitionType.Module)
    assertThat(companions.classApi()).isSameAs(classApi)
    assertThat(companions.objectApi()).isSameAs(objectApi)

    assertThat(analyzed.compilationTimestamp()).isEqualTo(123456789L)
    assertThat(analyzed.api()).isSameAs(companions)
    assertThat(analyzed.api()).isSameAs(companions)
    assertThat(companionEvaluations).isEqualTo(1)
    assertThat(analyzed.nameHashes()).containsExactly(nameHash)
    assertThat(analyzed.extraHash()).isEqualTo(99)
    assertThat(analyzed.withHasMacro(true).hasMacro()).isTrue()
    assertThat(nameHash.withScope(UseScope.Implicit).scope()).isSameAs(UseScope.Implicit)
  }

  @Test
  def dependencyDescriptorsLinkInternalAndExternalProductsToAnalyzedClasses(): Unit = {
    val companions = Companions.of(emptyClassApi("example.Source"), emptyClassApi("example.Source$"))
    val analyzedTarget = AnalyzedClass.of(
      1L,
      "example.Target",
      SafeLazy.strict(companions),
      2,
      Array(NameHash.of("apply", UseScope.Default, 3)),
      false
    )
    val internal = InternalDependency.of(
      "example.Source",
      "example.Target",
      DependencyContext.DependencyByInheritance
    )
    val external = ExternalDependency.of(
      "example.Source",
      "example.Target",
      analyzedTarget,
      DependencyContext.DependencyByMemberRef
    )

    assertThat(internal.sourceClassName()).isEqualTo("example.Source")
    assertThat(internal.targetClassName()).isEqualTo("example.Target")
    assertThat(internal.context()).isSameAs(DependencyContext.DependencyByInheritance)
    assertThat(internal.withContext(DependencyContext.LocalDependencyByInheritance).context())
      .isSameAs(DependencyContext.LocalDependencyByInheritance)
    assertThat(external.sourceClassName()).isEqualTo("example.Source")
    assertThat(external.targetProductClassName()).isEqualTo("example.Target")
    assertThat(external.targetClass()).isSameAs(analyzedTarget)
    assertThat(external.context()).isSameAs(DependencyContext.DependencyByMemberRef)
    assertThat(external.withTargetProductClassName("example.Target$package").targetProductClassName())
      .isEqualTo("example.Target$package")
  }

  @Test
  def compileOptionsMiniSetupAndPreviousResultsPreserveIncrementalConfiguration(): Unit = {
    val classpath = Array(new File("lib/scala-library.jar"), new File("lib/compiler-interface.jar"))
    val sources = Array(new File("src/main/scala/example/Service.scala"))
    val classesDirectory = new File("build/classes/scala/main")
    val temporaryClassesDirectory = new File("build/tmp/classes")
    val sourcePositionMapper = new Function[Position, Position] {
      override def apply(position: Position): Position = position
    }
    val compileOptions = CompileOptions.of(
      classpath,
      sources,
      classesDirectory,
      Array("-deprecation", "-feature"),
      Array("-Xlint:all"),
      7,
      sourcePositionMapper,
      CompileOrder.Mixed,
      Optional.of(temporaryClassesDirectory)
    )
    val classpathHash = FileHash.of(classpath(0), 123)
    val miniOptions = MiniOptions.of(Array(classpathHash), Array("-unchecked"), Array("-parameters"))
    val output = SingleOutputDirectory(new File("build/classes"))
    val extra = Array[T2[String, String]](Pair("analysis", "enabled"), Pair("scope", "test"))
    val miniSetup = MiniSetup.of(output, miniOptions, "2.13.16", CompileOrder.ScalaThenJava, true, extra)
    val previous = PreviousResult.of(Optional.empty[CompileAnalysis](), Optional.of(miniSetup))

    assertThat(compileOptions.classpath()).containsExactly(classpath: _*)
    assertThat(compileOptions.sources()).containsExactly(sources: _*)
    assertThat(compileOptions.classesDirectory()).isEqualTo(classesDirectory)
    assertThat(compileOptions.scalacOptions()).containsExactly("-deprecation", "-feature")
    assertThat(compileOptions.javacOptions()).containsExactly("-Xlint:all")
    assertThat(compileOptions.maxErrors()).isEqualTo(7)
    assertThat(compileOptions.sourcePositionMapper()).isSameAs(sourcePositionMapper)
    assertThat(compileOptions.order()).isSameAs(CompileOrder.Mixed)
    assertThat(compileOptions.temporaryClassesDirectory()).contains(temporaryClassesDirectory)
    assertThat(compileOptions.withMaxErrors(3).maxErrors()).isEqualTo(3)
    assertThat(compileOptions.withTemporaryClassesDirectory(Optional.empty[File]()).temporaryClassesDirectory()).isEmpty()

    assertThat(classpathHash.file()).isEqualTo(classpath(0))
    assertThat(classpathHash.hash()).isEqualTo(123)
    assertThat(classpathHash.withHash(456).hash()).isEqualTo(456)
    assertThat(miniOptions.classpathHash()).containsExactly(classpathHash)
    assertThat(miniOptions.scalacOptions()).containsExactly("-unchecked")
    assertThat(miniOptions.javacOptions()).containsExactly("-parameters")
    assertThat(miniOptions.withScalacOptions(Array("-Ywarn-unused")).scalacOptions()).containsExactly("-Ywarn-unused")

    assertThat(miniSetup.output()).isSameAs(output)
    assertThat(miniSetup.options()).isSameAs(miniOptions)
    assertThat(miniSetup.compilerVersion()).isEqualTo("2.13.16")
    assertThat(miniSetup.order()).isSameAs(CompileOrder.ScalaThenJava)
    assertThat(miniSetup.storeApis()).isTrue()
    assertThat(miniSetup.extra()).containsExactly(extra: _*)
    assertThat(miniSetup.withStoreApis(false).storeApis()).isFalse()
    assertThat(previous.analysis()).isEmpty()
    assertThat(previous.setup()).contains(miniSetup)
    assertThat(previous.withSetup(Optional.empty[MiniSetup]()).setup()).isEmpty()
  }

  @Test
  def compileAnalysisReadersAndResultsExposeIncrementalCompilationState(): Unit = {
    val source = new File("src/main/scala/example/Main.scala")
    val product = new File("build/classes/example/Main.class")
    val binary = new File("lib/dependency.jar")
    val output = SingleOutputDirectory(new File("build/classes/main"))
    val position = StaticPosition(
      Optional.of(JInteger.valueOf(2)),
      "object Main",
      Optional.of(JInteger.valueOf(7)),
      Optional.of(JInteger.valueOf(8)),
      Optional.of("       "),
      Optional.of(source.getPath),
      Optional.of(source)
    )
    val reportedProblem = DiagnosticProblem("lint", Severity.Warn, "unused import", position)
    val unreportedProblem = DiagnosticProblem("parser", Severity.Error, "incomplete input", position)
    val sourceInfo = StaticSourceInfo(Array(reportedProblem), Array(unreportedProblem), Array("example.Main"))
    val sourceStamp = StaticStamp(1, "hash:source", Optional.of("source-hash"), Optional.empty[JLong]())
    val productStamp = StaticStamp(2, "lastModified:product", Optional.empty[String](), Optional.of(JLong.valueOf(123456L)))
    val binaryStamp = StaticStamp(3, "hash:binary", Optional.of("binary-hash"), Optional.empty[JLong]())
    val sourceStamps = new LinkedHashMap[File, Stamp]()
    val productStamps = new LinkedHashMap[File, Stamp]()
    val binaryStamps = new LinkedHashMap[File, Stamp]()
    val sourceInfos = new LinkedHashMap[File, SourceInfo]()
    sourceStamps.put(source, sourceStamp)
    productStamps.put(product, productStamp)
    binaryStamps.put(binary, binaryStamp)
    sourceInfos.put(source, sourceInfo)
    val compilation = StaticCompilation(1234L, output)
    val analysis = StaticCompileAnalysis(
      StaticReadStamps(productStamps, sourceStamps, binaryStamps),
      StaticReadSourceInfos(sourceInfos),
      StaticReadCompilations(Array(compilation))
    )
    val setup = MiniSetup.of(
      output,
      MiniOptions.of(Array.empty[FileHash], Array("-deprecation"), Array("-parameters")),
      "scala-compiler",
      CompileOrder.Mixed,
      true,
      Array.empty[T2[String, String]]
    )
    val result = CompileResult.of(analysis, setup, true)

    assertThat(analysis.readStamps().source(source)).isSameAs(sourceStamp)
    assertThat(analysis.readStamps().source(source).getHash()).contains("source-hash")
    assertThat(analysis.readStamps().product(product).getLastModified()).contains(JLong.valueOf(123456L))
    assertThat(analysis.readStamps().binary(binary).writeStamp()).isEqualTo("hash:binary")
    assertThat(analysis.readStamps().getAllSourceStamps()).containsEntry(source, sourceStamp)
    assertThat(analysis.readStamps().getAllProductStamps()).containsEntry(product, productStamp)
    assertThat(analysis.readStamps().getAllBinaryStamps()).containsEntry(binary, binaryStamp)
    assertThat(analysis.readSourceInfos().get(source)).isSameAs(sourceInfo)
    assertThat(analysis.readSourceInfos().get(source).getReportedProblems()).containsExactly(reportedProblem)
    assertThat(analysis.readSourceInfos().get(source).getUnreportedProblems()).containsExactly(unreportedProblem)
    assertThat(analysis.readSourceInfos().get(source).getMainClasses()).containsExactly("example.Main")
    assertThat(analysis.readSourceInfos().getAllSourceInfos()).containsEntry(source, sourceInfo)
    assertThat(analysis.readCompilations().getAllCompilations()).containsExactly(compilation)
    assertThat(compilation.getStartTime()).isEqualTo(1234L)
    assertThat(compilation.getOutput()).isSameAs(output)
    assertThat(result.analysis()).isSameAs(analysis)
    assertThat(result.setup()).isSameAs(setup)
    assertThat(result.hasModified()).isTrue()
    assertThat(result.withHasModified(false).hasModified()).isFalse()
    assertThat(result.withSetup(setup.withStoreApis(false)).setup().storeApis()).isFalse()
  }

  @Test
  def outputsAndClasspathOptionsExposeSingleAndMultipleOutputModes(): Unit = {
    val mainClasses = new File("build/classes/main")
    val testClasses = new File("build/classes/test")
    val mainSources = new File("src/main/scala")
    val testSources = new File("src/test/scala")
    val singleOutput = SingleOutputDirectory(mainClasses)
    val groups = Array[OutputGroup](OutputDirectoryGroup(mainSources, mainClasses), OutputDirectoryGroup(testSources, testClasses))
    val multipleOutput = MultipleOutputDirectories(groups)
    val classpathOptions = ClasspathOptions.of(true, true, false, true, false)

    assertThat(singleOutput.getOutputDirectory()).isEqualTo(mainClasses)
    assertThat(singleOutput.getSingleOutput()).contains(mainClasses)
    assertThat(singleOutput.getMultipleOutput()).isEmpty()
    assertThat(multipleOutput.getOutputGroups()).containsExactly(groups: _*)
    assertThat(multipleOutput.getSingleOutput()).isEmpty()
    assertThat(multipleOutput.getMultipleOutput()).contains(groups)
    assertThat(groups(0).getSourceDirectory()).isEqualTo(mainSources)
    assertThat(groups(0).getOutputDirectory()).isEqualTo(mainClasses)

    assertThat(classpathOptions.bootLibrary()).isTrue()
    assertThat(classpathOptions.compiler()).isTrue()
    assertThat(classpathOptions.extra()).isFalse()
    assertThat(classpathOptions.autoBoot()).isTrue()
    assertThat(classpathOptions.filterLibrary()).isFalse()
    assertThat(classpathOptions.withExtra(true).extra()).isTrue()
    assertThat(classpathOptions.withFilterLibrary(true).filterLibrary()).isTrue()
  }

  @Test
  def incOptionsDefaultsWithersAndExternalHooksAreUsableWithoutCompilerImplementation(): Unit = {
    val externalClassFileManager = new RecordingClassFileManager("external")
    val externalHooks = new DefaultExternalHooks(Optional.empty[ExternalHooks.Lookup](), Optional.empty[ClassFileManager]())
      .withExternalClassFileManager(externalClassFileManager)
    val options = IncOptions.of()
      .withTransitiveStep(5)
      .withRecompileAllFraction(0.75d)
      .withRelationsDebug(true)
      .withApiDebug(true)
      .withApiDiffContextSize(9)
      .withApiDumpDirectory(Optional.of(new File("build/api-dump")))
      .withRecompileOnMacroDef(Optional.of(JBoolean.FALSE))
      .withUseOptimizedSealed(true)
      .withUseCustomizedFileManager(true)
      .withStoreApis(false)
      .withEnabled(false)
      .withExtra(java.util.Map.of("pipeline", "native-image"))
      .withLogRecompileOnMacro(true)
      .withIgnoredScalacOptions(Array("-Xplugin:*"))
      .withStrictMode(true)
      .withExternalHooks(externalHooks)

    assertThat(IncOptions.defaultTransitiveStep()).isEqualTo(3)
    assertThat(IncOptions.defaultRecompileAllFraction()).isEqualTo(0.5d)
    assertThat(IncOptions.defaultApiDiffContextSize()).isEqualTo(5)
    assertThat(IncOptions.defaultApiDumpDirectory()).isEmpty()
    assertThat(IncOptions.defaultClassFileManagerType()).isEmpty()
    assertThat(IncOptions.defaultRecompileOnMacroDef()).isEmpty()
    assertThat(IncOptions.defaultRecompileOnMacroDefImpl()).isTrue()
    assertThat(IncOptions.defaultUseCustomizedFileManager()).isFalse()
    assertThat(IncOptions.defaultStoreApis()).isTrue()
    assertThat(IncOptions.defaultEnabled()).isTrue()
    assertThat(IncOptions.defaultIgnoredScalacOptions()).isEmpty()

    assertThat(options.transitiveStep()).isEqualTo(5)
    assertThat(options.recompileAllFraction()).isEqualTo(0.75d)
    assertThat(options.relationsDebug()).isTrue()
    assertThat(options.apiDebug()).isTrue()
    assertThat(options.apiDiffContextSize()).isEqualTo(9)
    assertThat(options.apiDumpDirectory()).contains(new File("build/api-dump"))
    assertThat(options.recompileOnMacroDef()).contains(JBoolean.FALSE)
    assertThat(IncOptions.getRecompileOnMacroDef(options)).isFalse()
    assertThat(IncOptions.getRecompileOnMacroDef(IncOptions.of())).isEqualTo(IncOptions.defaultRecompileOnMacroDefImpl())
    assertThat(options.useOptimizedSealed()).isTrue()
    assertThat(options.useCustomizedFileManager()).isTrue()
    assertThat(options.storeApis()).isFalse()
    assertThat(options.enabled()).isFalse()
    assertThat(options.extra()).containsEntry("pipeline", "native-image")
    assertThat(options.logRecompileOnMacro()).isTrue()
    assertThat(options.ignoredScalacOptions()).containsExactly("-Xplugin:*")
    assertThat(options.strictMode()).isTrue()
    assertThat(options.externalHooks().getExternalClassFileManager()).contains(externalClassFileManager)
    assertThat(IncToolOptionsUtil.defaultIncToolOptions().classFileManager()).isEmpty()
    assertThat(IncToolOptionsUtil.defaultIncToolOptions().useCustomizedFileManager()).isFalse()
  }

  @Test
  def wrappedClassFileManagerDelegatesToExternalThenInternalManagers(): Unit = {
    val internal = new RecordingClassFileManager("internal")
    val external = new RecordingClassFileManager("external")
    val generated = Array(new File("build/classes/example/Generated.class"))
    val deleted = Array(new File("build/classes/example/Deleted.class"))
    val wrapped = WrappedClassFileManager.of(internal, Optional.of(external))

    wrapped.generated(generated)
    wrapped.delete(deleted)
    wrapped.complete(true)

    assertThat(external.events).containsExactly(
      "external:generated:build/classes/example/Generated.class",
      "external:delete:build/classes/example/Deleted.class",
      "external:complete:true"
    )
    assertThat(internal.events).containsExactly(
      "internal:generated:build/classes/example/Generated.class",
      "internal:delete:build/classes/example/Deleted.class",
      "internal:complete:true"
    )

    val internalOnly = new RecordingClassFileManager("internal-only")
    WrappedClassFileManager.of(internalOnly, Optional.empty[ClassFileManager]()).complete(false)
    assertThat(internalOnly.events).containsExactly("internal-only:complete:false")
  }

  @Test
  def compilerInputsCollectCompilersSetupAndPreviousResults(): Unit = {
    val scalaInstance = TestScalaInstance(
      versionValue = "2.13.16",
      libraryJarValues = Array(new File("lib/scala-library.jar")),
      compilerJarValue = new File("lib/scala-compiler.jar"),
      otherJarValues = Array(new File("lib/scala-reflect.jar"))
    )
    val scalaCompiler = new RecordingScalaCompiler(scalaInstance, ClasspathOptions.of(true, true, true, true, true))
    val javac = new RecordingJavaTool("javac")
    val javadoc = new RecordingJavaTool("javadoc")
    val javaTools = TestJavaTools(javac, javadoc)
    val compilers = Compilers.of(scalaCompiler, javaTools)
    val lookup = EmptyClasspathLookup
    val reporter = new RecordingReporter()
    val cache = new RecordingGlobalsCache()
    val progress = new RecordingProgress()
    val setup = Setup.of(
      lookup,
      false,
      new File("build/inc/compile.analysis"),
      cache,
      IncOptions.of(),
      reporter,
      Optional.of(progress),
      Array[T2[String, String]](Pair("origin", "test"))
    )
    val compileOptions = CompileOptions.of().withSources(Array(new File("src/main/scala/Main.scala")))
    val previous = PreviousResult.of(Optional.empty[CompileAnalysis](), Optional.empty[MiniSetup]())
    val inputs = Inputs.of(compilers, compileOptions, setup, previous)

    assertThat(scalaInstance.version()).isEqualTo("2.13.16")
    assertThat(scalaInstance.actualVersion()).isEqualTo("2.13.16")
    assertThat(scalaInstance.libraryJar()).isEqualTo(new File("lib/scala-library.jar"))
    assertThat(scalaInstance.allJars()).containsExactly(
      new File("lib/scala-library.jar"),
      new File("lib/scala-compiler.jar"),
      new File("lib/scala-reflect.jar")
    )
    assertThat(compilers.scalac()).isSameAs(scalaCompiler)
    assertThat(compilers.javaTools().javac()).isSameAs(javac)
    assertThat(compilers.javaTools().javadoc()).isSameAs(javadoc)
    assertThat(setup.perClasspathEntryLookup()).isSameAs(lookup)
    assertThat(setup.skip()).isFalse()
    assertThat(setup.cache()).isSameAs(cache)
    assertThat(setup.reporter()).isSameAs(reporter)
    assertThat(setup.progress()).contains(progress)
    assertThat(setup.extra()(0).get1()).isEqualTo("origin")
    assertThat(inputs.compilers()).isSameAs(compilers)
    assertThat(inputs.options()).isSameAs(compileOptions)
    assertThat(inputs.setup()).isSameAs(setup)
    assertThat(inputs.previousResult()).isSameAs(previous)
    assertThat(inputs.withSetup(setup.withSkip(true)).setup().skip()).isTrue()
  }

  @Test
  def reporterCompileProgressAndCompileExceptionsExposeDiagnostics(): Unit = {
    val position = StaticPosition(
      lineNumber = Optional.of(JInteger.valueOf(12)),
      text = "val answer: String = 42",
      characterOffset = Optional.of(JInteger.valueOf(19)),
      pointerColumn = Optional.of(JInteger.valueOf(20)),
      pointerPadding = Optional.of("                   "),
      path = Optional.of("src/main/scala/example/Answer.scala"),
      file = Optional.empty[File]()
    )
    val problem = DiagnosticProblem(
      diagnosticCategory = "type-mismatch",
      diagnosticSeverity = Severity.Error,
      diagnosticMessage = "found Int, required String",
      diagnosticPosition = position
    )
    val reporter = new RecordingReporter()
    val progress = new RecordingProgress()
    val failed = new TestCompileFailed(Array("-deprecation"), Array(problem))
    val cancelled = new TestCompileCancelled(Array("-Ystop-after:typer"))

    reporter.log(problem)
    reporter.comment(position, "check inferred type")
    progress.startUnit("example.Answer", "src/main/scala/example/Answer.scala")
    assertThat(progress.advance(1, 3)).isTrue()
    assertThat(progress.advance(3, 3)).isFalse()

    assertThat(reporter.hasErrors()).isTrue()
    assertThat(reporter.hasWarnings()).isFalse()
    assertThat(reporter.problems()).containsExactly(problem)
    assertThat(reporter.comments).containsExactly("src/main/scala/example/Answer.scala:check inferred type")
    assertThat(position.line()).contains(JInteger.valueOf(12))
    assertThat(position.renderPointer).isEqualTo("                   ^")
    assertThat(problem.category()).isEqualTo("type-mismatch")
    assertThat(problem.rendered()).isEmpty()
    assertThat(progress.events).containsExactly(
      "start:example.Answer:src/main/scala/example/Answer.scala",
      "advance:1/3",
      "advance:3/3"
    )
    assertThat(failed.arguments()).containsExactly("-deprecation")
    assertThat(failed.problems()).containsExactly(problem)
    assertThat(cancelled.arguments()).containsExactly("-Ystop-after:typer")
  }

  @Test
  def advancedApiTypesModelPolymorphicExistentialAnnotatedAndPathDependentShapes(): Unit = {
    val scalaPath = Path.of(Array[PathComponent](Id.of("scala")))
    val anyType = Projection.of(Singleton.of(scalaPath), "Any")
    val anyRefType = Projection.of(Singleton.of(scalaPath), "AnyRef")
    val ownerPath = Path.of(Array[PathComponent](This.of(), Id.of("Outer")))
    val ownerMember = Projection.of(Singleton.of(ownerPath), "Member")
    val typeParameterRef = ParameterRef.of("F")
    val higherKindedParameter = TypeParameter.of(
      "F",
      Array.empty[Annotation],
      Array.empty[TypeParameter],
      Variance.Invariant,
      EmptyType.of(),
      anyType
    )
    val valueParameter = TypeParameter.of(
      "A",
      Array.empty[Annotation],
      Array.empty[TypeParameter],
      Variance.Covariant,
      EmptyType.of(),
      anyRefType
    )
    val polymorphicMember = Polymorphic.of(ownerMember, Array(higherKindedParameter))
    val appliedParameter = Parameterized.of(typeParameterRef, Array(ownerMember))
    val existentialAppliedParameter = Existential.of(appliedParameter, Array(valueParameter))
    val uncheckedAnnotation = Annotation.of(
      Projection.of(Singleton.of(scalaPath), "unchecked"),
      Array.empty[AnnotationArgument]
    )
    val annotatedExistential = Annotated.of(existentialAppliedParameter, Array(uncheckedAnnotation))
    val superQualifierPath = Path.of(Array[PathComponent](Id.of("example")))
    val superPathComponent = Super.of(superQualifierPath)
    val inheritedPath = Path.of(Array[PathComponent](superPathComponent, Id.of("Ops")))
    val updatedSuper = superPathComponent.withQualifier(Path.of(Array[PathComponent](Id.of("api"))))

    assertThat(typeParameterRef.id()).isEqualTo("F")
    assertThat(typeParameterRef.withId("G").id()).isEqualTo("G")
    assertThat(polymorphicMember.baseType()).isEqualTo(ownerMember)
    assertThat(polymorphicMember.parameters()).containsExactly(higherKindedParameter)
    assertThat(polymorphicMember.withBaseType(anyType).baseType()).isEqualTo(anyType)
    assertThat(polymorphicMember.withParameters(Array(valueParameter)).parameters()).containsExactly(valueParameter)
    assertThat(existentialAppliedParameter.baseType()).isEqualTo(appliedParameter)
    assertThat(existentialAppliedParameter.clause()).containsExactly(valueParameter)
    assertThat(existentialAppliedParameter.withBaseType(ownerMember).baseType()).isEqualTo(ownerMember)
    assertThat(existentialAppliedParameter.withClause(Array(higherKindedParameter)).clause()).containsExactly(higherKindedParameter)
    assertThat(annotatedExistential.baseType()).isEqualTo(existentialAppliedParameter)
    assertThat(annotatedExistential.annotations()).containsExactly(uncheckedAnnotation)
    assertThat(annotatedExistential.withAnnotations(Array.empty[Annotation]).annotations()).isEmpty()
    assertThat(Private.of(Unqualified.of()).qualifier()).isEqualTo(Unqualified.of())
    assertThat(inheritedPath.components()).containsExactly(superPathComponent, Id.of("Ops"))
    assertThat(updatedSuper.qualifier().components()).containsExactly(Id.of("api"))
  }

  @Test
  def analysisCallbackImplementationsCanCaptureCompilerEvents(): Unit = {
    val callback = new RecordingAnalysisCallback(true)
    val source = new File("src/main/scala/example/Service.scala")
    val binary = new File("lib/dependency.jar")
    val product = new File("build/classes/example/Service.class")
    val api = emptyClassApi("example.Service")
    val position = StaticPosition(
      Optional.of(JInteger.valueOf(1)),
      "object Service",
      Optional.of(JInteger.valueOf(0)),
      Optional.of(JInteger.valueOf(1)),
      Optional.of(""),
      Optional.of(source.getPath),
      Optional.of(source)
    )

    callback.startSource(source)
    callback.classDependency("example.Service", "example.Dependency", DependencyContext.DependencyByMemberRef)
    callback.binaryDependency(binary, "example.Service", "example.External", product, DependencyContext.DependencyByInheritance)
    callback.generatedNonLocalClass(source, product, "example.Service", "example.Service")
    callback.generatedLocalClass(source, product)
    callback.api(source, api)
    callback.mainClass(source, "example.Service")
    callback.usedName("example.Service", "apply", EnumSet.of(UseScope.Default, UseScope.Implicit))
    callback.problem("type-mismatch", position, "bad type", Severity.Error, true)
    callback.dependencyPhaseCompleted()
    callback.apiPhaseCompleted()

    assertThat(callback.enabled()).isTrue()
    assertThat(callback.events).containsExactly(
      "source:src/main/scala/example/Service.scala",
      "class-dependency:example.Service->example.Dependency:DependencyByMemberRef",
      "binary-dependency:lib/dependency.jar:example.Service->example.External:build/classes/example/Service.class:DependencyByInheritance",
      "generated-non-local:src/main/scala/example/Service.scala:build/classes/example/Service.class:example.Service:example.Service",
      "generated-local:src/main/scala/example/Service.scala:build/classes/example/Service.class",
      "api:src/main/scala/example/Service.scala:example.Service",
      "main:src/main/scala/example/Service.scala:example.Service",
      "used-name:example.Service:apply:Default,Implicit",
      "problem:type-mismatch:bad type:Error:true",
      "dependency-phase-completed",
      "api-phase-completed"
    )
    assertThat(callback.classesInOutputJar()).contains("example.Service")
  }

  private def noModifiers: Modifiers = new Modifiers(false, false, false, false, false, false, false, false)

  private implicit class ModifierOps(private val modifiers: Modifiers) {
    def withLazyFlag: Modifiers = new Modifiers(
      modifiers.isAbstract,
      modifiers.isOverride,
      modifiers.isFinal,
      modifiers.isSealed,
      modifiers.isImplicit,
      true,
      modifiers.isMacro,
      modifiers.isSuperAccessor
    )
  }

  private def emptyClassApi(name: String): ClassLike = ClassLike.of(
    name,
    Public.of(),
    noModifiers,
    Array.empty[Annotation],
    DefinitionType.ClassDef,
    SafeLazy.strict(EmptyType.of()),
    SafeLazy.strict(Structure.of(
      SafeLazy.strict(Array.empty[Type]),
      SafeLazy.strict(Array.empty[ClassDefinition]),
      SafeLazy.strict(Array.empty[ClassDefinition])
    )),
    Array.empty[String],
    Array.empty[Type],
    true,
    Array.empty[TypeParameter]
  )

  private final case class Pair[A1, A2](first: A1, second: A2) extends T2[A1, A2] {
    override def get1(): A1 = first

    override def get2(): A2 = second
  }

  private final case class SingleOutputDirectory(outputDirectory: File) extends SingleOutput {
    override def getOutputDirectory(): File = outputDirectory
  }

  private final case class MultipleOutputDirectories(outputGroups: Array[OutputGroup]) extends MultipleOutput {
    override def getOutputGroups(): Array[OutputGroup] = outputGroups
  }

  private final case class OutputDirectoryGroup(sourceDirectory: File, outputDirectory: File) extends OutputGroup {
    override def getSourceDirectory(): File = sourceDirectory

    override def getOutputDirectory(): File = outputDirectory
  }

  private final case class StaticCompileAnalysis(
    stamps: ReadStamps,
    sourceInfos: ReadSourceInfos,
    compilations: ReadCompilations
  ) extends CompileAnalysis {
    override def readStamps(): ReadStamps = stamps

    override def readSourceInfos(): ReadSourceInfos = sourceInfos

    override def readCompilations(): ReadCompilations = compilations
  }

  private final case class StaticCompilation(startTime: Long, output: Output) extends Compilation {
    override def getStartTime(): Long = startTime

    override def getOutput(): Output = output
  }

  private final case class StaticReadCompilations(compilations: Array[Compilation]) extends ReadCompilations {
    override def getAllCompilations(): Array[Compilation] = compilations
  }

  private final case class StaticReadSourceInfos(sourceInfos: JMap[File, SourceInfo]) extends ReadSourceInfos {
    override def get(sourceFile: File): SourceInfo = sourceInfos.get(sourceFile)

    override def getAllSourceInfos(): JMap[File, SourceInfo] = sourceInfos
  }

  private final case class StaticReadStamps(
    productStamps: JMap[File, Stamp],
    sourceStamps: JMap[File, Stamp],
    binaryStamps: JMap[File, Stamp]
  ) extends ReadStamps {
    override def product(productFile: File): Stamp = productStamps.get(productFile)

    override def source(sourceFile: File): Stamp = sourceStamps.get(sourceFile)

    override def binary(binaryFile: File): Stamp = binaryStamps.get(binaryFile)

    override def getAllBinaryStamps(): JMap[File, Stamp] = binaryStamps

    override def getAllSourceStamps(): JMap[File, Stamp] = sourceStamps

    override def getAllProductStamps(): JMap[File, Stamp] = productStamps
  }

  private final case class StaticStamp(
    valueId: Int,
    stampValue: String,
    hashValue: Optional[String],
    lastModifiedValue: Optional[JLong]
  ) extends Stamp {
    override def getValueId(): Int = valueId

    override def writeStamp(): String = stampValue

    override def getHash(): Optional[String] = hashValue

    override def getLastModified(): Optional[JLong] = lastModifiedValue
  }

  private final case class StaticSourceInfo(
    reportedProblems: Array[Problem],
    unreportedProblems: Array[Problem],
    mainClasses: Array[String]
  ) extends SourceInfo {
    override def getReportedProblems(): Array[Problem] = reportedProblems

    override def getUnreportedProblems(): Array[Problem] = unreportedProblems

    override def getMainClasses(): Array[String] = mainClasses
  }

  private final class RecordingClassFileManager(label: String) extends ClassFileManager {
    val events: ArrayList[String] = new ArrayList[String]()

    override def delete(files: Array[File]): Unit = events.add(s"$label:delete:${files.map(_.getPath).mkString(",")}")

    override def generated(files: Array[File]): Unit = events.add(s"$label:generated:${files.map(_.getPath).mkString(",")}")

    override def complete(success: Boolean): Unit = events.add(s"$label:complete:$success")
  }

  private final case class TestScalaInstance(
    versionValue: String,
    libraryJarValues: Array[File],
    compilerJarValue: File,
    otherJarValues: Array[File]
  ) extends ScalaInstance {
    override def version(): String = versionValue

    override def loader(): ClassLoader = getClass.getClassLoader

    override def loaderLibraryOnly(): ClassLoader = getClass.getClassLoader

    override def libraryJars(): Array[File] = libraryJarValues

    override def compilerJar(): File = compilerJarValue

    override def otherJars(): Array[File] = otherJarValues

    override def allJars(): Array[File] = libraryJarValues ++ Array(compilerJarValue) ++ otherJarValues

    override def actualVersion(): String = versionValue
  }

  private final class RecordingScalaCompiler(
    instance: ScalaInstance,
    options: ClasspathOptions
  ) extends ScalaCompiler {
    val invocations: ArrayList[String] = new ArrayList[String]()

    override def scalaInstance(): ScalaInstance = instance

    override def classpathOptions(): ClasspathOptions = options

    override def compile(
      sources: Array[File],
      changes: DependencyChanges,
      callback: xsbti.AnalysisCallback,
      log: xsbti.Logger,
      reporter: Reporter,
      progress: CompileProgress,
      cached: CachedCompiler
    ): Unit = invocations.add(s"cached:${sources.length}")

    override def compile(
      sources: Array[File],
      changes: DependencyChanges,
      options: Array[String],
      output: Output,
      callback: xsbti.AnalysisCallback,
      reporter: Reporter,
      cache: GlobalsCache,
      log: xsbti.Logger,
      progress: Optional[CompileProgress]
    ): Unit = invocations.add(s"direct:${sources.length}:${options.mkString(",")}")
  }

  private final class RecordingJavaTool(label: String) extends JavaCompiler with Javadoc {
    val invocations: ArrayList[String] = new ArrayList[String]()

    override def run(
      sources: Array[File],
      options: Array[String],
      incToolOptions: IncToolOptions,
      reporter: Reporter,
      log: xsbti.Logger
    ): Boolean = {
      invocations.add(s"$label:${sources.map(_.getPath).mkString(",")}:${options.mkString(",")}")
      true
    }
  }

  private final case class TestJavaTools(javaCompiler: JavaCompiler, javaDocTool: Javadoc) extends JavaTools {
    override def javac(): JavaCompiler = javaCompiler

    override def javadoc(): Javadoc = javaDocTool
  }

  private object EmptyClasspathLookup extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Optional[CompileAnalysis] = Optional.empty[CompileAnalysis]()

    override def definesClass(classpathEntry: File): DefinesClass = new DefinesClass {
      override def apply(className: String): Boolean = false
    }
  }

  private final class RecordingGlobalsCache extends GlobalsCache {
    val events: ArrayList[String] = new ArrayList[String]()

    override def apply(
      args: Array[String],
      output: Output,
      forceNew: Boolean,
      provider: CachedCompilerProvider,
      log: xsbti.Logger,
      reporter: Reporter
    ): CachedCompiler = {
      events.add(s"apply:${args.mkString(",")}:$forceNew")
      new CachedCompiler {
        override def commandArguments(sources: Array[File]): Array[String] = args ++ sources.map(_.getPath)

        override def run(
          sources: Array[File],
          changes: DependencyChanges,
          callback: xsbti.AnalysisCallback,
          log: xsbti.Logger,
          reporter: Reporter,
          progress: CompileProgress
        ): Unit = events.add(s"run:${sources.length}")
      }
    }

    override def clear(): Unit = events.add("clear")
  }

  private final class RecordingProgress extends CompileProgress {
    val events: ArrayList[String] = new ArrayList[String]()

    override def startUnit(phase: String, unitPath: String): Unit = events.add(s"start:$phase:$unitPath")

    override def advance(current: Int, total: Int): Boolean = {
      events.add(s"advance:$current/$total")
      current < total
    }
  }

  private final case class StaticPosition(
    lineNumber: Optional[JInteger],
    text: String,
    characterOffset: Optional[JInteger],
    pointerColumn: Optional[JInteger],
    pointerPadding: Optional[String],
    path: Optional[String],
    file: Optional[File]
  ) extends Position {
    override def line(): Optional[JInteger] = lineNumber

    override def lineContent(): String = text

    override def offset(): Optional[JInteger] = characterOffset

    override def pointer(): Optional[JInteger] = pointerColumn

    override def pointerSpace(): Optional[String] = pointerPadding

    override def sourcePath(): Optional[String] = path

    override def sourceFile(): Optional[File] = file

    def renderPointer: String = pointerSpace().orElse("") + "^"
  }

  private final case class DiagnosticProblem(
    diagnosticCategory: String,
    diagnosticSeverity: Severity,
    diagnosticMessage: String,
    diagnosticPosition: Position
  ) extends Problem {
    override def category(): String = diagnosticCategory

    override def severity(): Severity = diagnosticSeverity

    override def message(): String = diagnosticMessage

    override def position(): Position = diagnosticPosition
  }

  private final class RecordingReporter extends Reporter {
    private val recordedProblems: ArrayList[Problem] = new ArrayList[Problem]()
    val comments: ArrayList[String] = new ArrayList[String]()

    override def reset(): Unit = {
      recordedProblems.clear()
      comments.clear()
    }

    override def hasErrors(): Boolean = recordedProblems.stream().anyMatch(_.severity() == Severity.Error)

    override def hasWarnings(): Boolean = recordedProblems.stream().anyMatch(_.severity() == Severity.Warn)

    override def printSummary(): Unit = comments.add(s"summary:${recordedProblems.size()}")

    override def problems(): Array[Problem] = recordedProblems.toArray(new Array[Problem](recordedProblems.size()))

    override def log(problem: Problem): Unit = recordedProblems.add(problem)

    override def comment(position: Position, message: String): Unit =
      comments.add(s"${position.sourcePath().orElse("<unknown>")}:$message")
  }

  private final class TestCompileFailed(compilerArguments: Array[String], compilerProblems: Array[Problem])
      extends xsbti.CompileFailed {
    override def arguments(): Array[String] = compilerArguments

    override def problems(): Array[Problem] = compilerProblems
  }

  private final class TestCompileCancelled(compilerArguments: Array[String]) extends xsbti.CompileCancelled {
    override def arguments(): Array[String] = compilerArguments
  }

  private final class RecordingAnalysisCallback(enabledValue: Boolean) extends xsbti.AnalysisCallback {
    val events: ArrayList[String] = new ArrayList[String]()
    private val outputJarClasses: HashSet[String] = new HashSet[String]()

    override def startSource(source: File): Unit = events.add(s"source:${source.getPath}")

    override def classDependency(sourceClassName: String, targetClassName: String, context: DependencyContext): Unit =
      events.add(s"class-dependency:$sourceClassName->$targetClassName:$context")

    override def binaryDependency(
      binary: File,
      sourceClassName: String,
      targetClassName: String,
      classFile: File,
      context: DependencyContext
    ): Unit = events.add(s"binary-dependency:${binary.getPath}:$sourceClassName->$targetClassName:${classFile.getPath}:$context")

    override def generatedNonLocalClass(source: File, classFile: File, binaryClassName: String, srcClassName: String): Unit = {
      outputJarClasses.add(binaryClassName)
      events.add(s"generated-non-local:${source.getPath}:${classFile.getPath}:$binaryClassName:$srcClassName")
    }

    override def generatedLocalClass(source: File, classFile: File): Unit =
      events.add(s"generated-local:${source.getPath}:${classFile.getPath}")

    override def api(sourceFile: File, classApi: ClassLike): Unit = events.add(s"api:${sourceFile.getPath}:${classApi.name()}")

    override def mainClass(sourceFile: File, className: String): Unit = events.add(s"main:${sourceFile.getPath}:$className")

    override def usedName(className: String, name: String, scopes: EnumSet[UseScope]): Unit =
      events.add(s"used-name:$className:$name:${scopes.toArray.mkString(",")}")

    override def problem(category: String, position: Position, message: String, severity: Severity, reported: Boolean): Unit =
      events.add(s"problem:$category:$message:$severity:$reported")

    override def dependencyPhaseCompleted(): Unit = events.add("dependency-phase-completed")

    override def apiPhaseCompleted(): Unit = events.add("api-phase-completed")

    override def enabled(): Boolean = enabledValue

    override def classesInOutputJar(): JSet[String] = outputJarClasses
  }
}
