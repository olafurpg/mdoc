package minc.internal.bloops

import bloop.config.{Config => C}
import mdoc.internal.markdown.FileImport
import mdoc.internal.pos.PositionSyntax._
import mdoc.internal.BuildInfo
import coursierapi.Dependency
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.meta.io.AbsolutePath
import minc.internal.commands.MD5
import scala.meta.internal.io.ListFiles
import scala.meta.internal.io.FileIO
import java.nio.file.FileSystems
import java.nio.file.Files
import mdoc.internal.cli.Dependencies

class BloopProjects(in: Inputs) {
  val allScalaJars = coursierapi.Fetch
    .create()
    .addDependencies(Dependency.of("org.scala-lang", "scala-compiler", BuildInfo.scalaVersion))
    .fetch()
    .asScala
    .map(_.toPath())
    .toList
  val classpath = Dependencies.fetchClasspath(in.instrumented, in.settings) match {
    case Nil => allScalaJars
    case els => els
  }
  val isGenerated = new mutable.LinkedHashMap[AbsolutePath, C.File]
  val isGeneratedJsonFile = mutable.Set.empty[AbsolutePath]
  val sourceByInstrumentedScript = mutable.Map.empty[AbsolutePath, AbsolutePath]
  def file(i: FileImport): C.File = {
    isGenerated.getOrElseUpdate(i.path, fileUncached(i))
  }
  private def fileUncached(i: FileImport): C.File = {
    val directoryName = BloopProjects.directoryName(i.path)
    val fileOutputDirectory = in.bloopDir.resolve(directoryName).createDirectories
    val dependencies = i.dependencies.map(dep => this.file(dep))
    val classesDir = fileOutputDirectory.resolve("classes").createDirectories
    val projectClasspath = dependencies.map(_.project.classesDir) ++ classpath
    pprint.log(in.workspace)
    val instrumentedScript = in.workspace.resolve(directoryName + ".scala")
    val file = C.File(
      "1.4.0",
      C.Project(
        name = i.path.toString(),
        directory = i.path.parent.toNIO,
        workspaceDir = Some(in.app.workingDirectory),
        sources = List(instrumentedScript.toNIO),
        sourcesGlobs = None,
        sourceRoots = None,
        dependencies = dependencies.map(_.project.name),
        classpath = projectClasspath,
        out = fileOutputDirectory.toNIO,
        classesDir = classesDir.toNIO,
        resources = None,
        scala = bloopScala(in.instrumented.scalacOptions),
        java = Some(
          C.Java(
            List(s"-Duser.dir${in.app.workingDirectory}")
          )
        ),
        sbt = None,
        test = None,
        platform = None,
        resolution = None,
        tags = None
      )
    )

    sourceByInstrumentedScript(instrumentedScript) = i.path
    instrumentedScript.write(i.toInput.text)
    val jsonFile = in.bloopDir.resolve(directoryName + ".json")
    jsonFile.write(bloop.config.write(file))
    isGeneratedJsonFile += jsonFile
    file
  }
  val jsonPattern = FileSystems.getDefault().getPathMatcher("glob:**/*.json")
  def garbageCollectBloopJson(): Unit = {
    FileIO.listFiles(in.bloopDir).foreach { path =>
      if (!isGeneratedJsonFile.contains(path) &&
        jsonPattern.matches(path.toNIO) &&
        !path.toNIO.endsWith("bloop.settings.json") &&
        path.isFile) {
        Files.delete(path.toNIO)
      }
    }
  }

  def bloopScala(scalacOptions: List[String]): Option[C.Scala] =
    Some(
      C.Scala(
        "org.scala-lang",
        "scala-compiler",
        BuildInfo.scalaVersion,
        scalacOptions,
        allScalaJars.toList,
        None,
        setup = Some(
          C.CompileSetup(
            C.Mixed,
            addLibraryToBootClasspath = true,
            addCompilerToClasspath = false,
            addExtraJarsToClasspath = false,
            manageBootClasspath = true,
            filterLibraryFromClasspath = true
          )
        )
      )
    )
}

object BloopProjects {
  def directoryName(path: AbsolutePath): String = {
    val md5 = MD5.compute(path.toURI.toString()).take(6)
    path.filename + "_" + md5
  }
  def create(inputs: Inputs): C.Project = {
    val projects = new BloopProjects(inputs)
    val main = projects.file(inputs.main)
    projects.garbageCollectBloopJson()
    main.project
  }
}
