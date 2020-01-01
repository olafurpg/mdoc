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

@deprecated("Use OnStartContext instead", "2.2.0")
final class OnLoadContext private[mdoc] (
    val reporter: Reporter,
    private[mdoc] val settings: Settings
) {
  def site: Map[String, String] = settings.site
}
