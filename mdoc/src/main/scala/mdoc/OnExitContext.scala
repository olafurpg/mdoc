package mdoc

import java.util.ServiceLoader
import mdoc.internal.cli.Settings
import metaconfig.ConfDecoder
import metaconfig.ConfEncoder
import metaconfig.ConfError
import metaconfig.generic.Surface
import scala.meta.inputs.Input
import scala.meta.io.AbsolutePath
import scala.collection.JavaConverters._
import scala.meta.io.RelativePath
import mdoc.internal.pos.PositionSyntax._
import scala.meta.inputs.Position

final class OnExitContext private[mdoc] (
    val reporter: Reporter,
    val exit: MainExit,
    private[mdoc] val settings: Settings
) {
  def site: Map[String, String] = settings.site
}
