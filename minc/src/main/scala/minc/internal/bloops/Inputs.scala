package minc.internal.bloops

import mdoc.internal.markdown.Instrumented
import metaconfig.cli.CliApp
import scala.meta.io.AbsolutePath
import mdoc.internal.cli.Settings
import mdoc.Reporter
import mdoc.internal.pos.PositionSyntax._
import mdoc.internal.markdown.FileImport
import scala.meta.Name
import scala.meta.Term

case class Inputs(
    instrumented: Instrumented,
    workspace: AbsolutePath,
    settings: Settings,
    reporter: Reporter,
    app: CliApp
) {
  val bloopDir = workspace.resolve(".bloop").createDirectories
  val main = FileImport(
    instrumented.file.inputFile,
    Term.Name("a"),
    Name.Indeterminate("a"),
    instrumented.file.inputFile.filename.stripSuffix(".sc"),
    "$file",
    instrumented.file.inputFile.readText,
    instrumented.fileImports,
    Nil,
    isMainClass = true
  )
}
