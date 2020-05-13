package minc.internal.bloops

import mdoc.internal.markdown.Instrumented
import metaconfig.cli.CliApp
import scala.meta.io.AbsolutePath
import mdoc.internal.cli.Settings
import mdoc.Reporter
import mdoc.internal.pos.PositionSyntax._
import mdoc.internal.markdown.FileImport

case class Inputs(
    instrumented: Instrumented,
    outdir: AbsolutePath,
    settings: Settings,
    reporter: Reporter,
    app: CliApp
) {
  val bloopDir = outdir.resolve(".bloop").createDirectories
  val main = FileImport(instrumented.source)
}
