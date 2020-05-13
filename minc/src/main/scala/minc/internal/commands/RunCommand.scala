package minc.internal.commands

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import mdoc.internal.worksheets.Mdoc
import mdoc.MainSettings
import scala.meta.io.AbsolutePath
import mdoc.internal.pos.PositionSyntax._
import mdoc.internal.cli.InputFile
import mdoc.internal.cli.Settings
import mdoc.internal.markdown.Instrumenter
import mdoc.internal.markdown.MdocDialect
import scala.meta.inputs.Input
import scala.meta.Source
import scala.meta.parsers.Parsed.Success
import mdoc.internal.markdown.Modifier
import mdoc.internal.markdown.SectionInput
import mdoc.internal.io.ConsoleReporter
import scala.meta.io.RelativePath
import io.github.soc.directories.ProjectDirectories
import mdoc.internal.markdown.Instrumented
import mdoc.Reporter
import java.nio.file.Files
import minc.internal.bloops.Inputs
import minc.internal.bloops.BloopProjects

object RunCommand extends Command[RunOptions]("run") {
  def run(value: RunOptions, app: CliApp): Int = {
    value.scripts match {
      case Nil =>
        app.error(
          "missing script argument. To fix this problem pass in an argument like this:\n\tminc my-app.sc"
        )
        1
      case userPath :: Nil =>
        val cwd = AbsolutePath(app.workingDirectory)
        val path = AbsolutePath(userPath)(cwd)
        if (path.isDirectory) {
          app.error(
            "cannot run a directory. To fix this problem, pass in another argument that is a regular file."
          )
          1
        } else if (!path.isFile) {
          val tab = "\t"
          app.error(
            s"""file does not exist '$path'. To fix this problem, create a new script in this path:
               |${tab}echo 'println("Hello world!")' > $userPath
               |${tab}minc $userPath
               |""".stripMargin
          )
          1
        } else {
          val filename = path.filename
          val text = path.readText
          val input = Input.VirtualFile(filename, text)
          MdocDialect.scala(input).parse[Source] match {
            case e: scala.meta.parsers.Parsed.Error =>
              app.error(e.pos.formatMessage("error", e.message))
              1
            case Success(source) =>
              val cacheDir = AbsolutePath(
                ProjectDirectories.from("org.scalameta", "Scalameta", "minc").cacheDir
              )
              val outdir = cacheDir.resolve(BloopProjects.directoryName(path)).createDirectories
              val reporter = new ConsoleReporter(app.out)
              val settings =
                Settings.default(cwd).copy(in = List(path.parent), out = List(outdir))
              val file = InputFile.fromRelativeFilename(path.filename, settings)
              val sectionInput = SectionInput(
                input,
                source,
                Modifier.Default()
              )
              val sectionInputs = List(sectionInput)
              val instrumented = Instrumenter.instrument(file, sectionInputs, settings, reporter)
              if (reporter.hasErrors) 1
              else BloopProjects.create(Inputs(instrumented, outdir, settings, reporter, app))
          }
        }
        0
      case many =>
        app.error(
          "too many script arguments. To fix this problem pass in only one script:\n\tmind my-app.sc"
        )
        1
    }
    1
  }
}
