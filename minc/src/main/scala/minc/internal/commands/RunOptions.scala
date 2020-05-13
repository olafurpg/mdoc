package minc.internal.commands

import java.nio.file.Path
import metaconfig.annotation.ExtraName

final case class RunOptions(
    @ExtraName("remainingArgs")
    scripts: List[Path] = Nil
)
object RunOptions {
  val default = RunOptions()
  implicit lazy val surface = metaconfig.generic.deriveSurface[RunOptions]
  implicit lazy val codec = metaconfig.generic.deriveCodec[RunOptions](default)
}
