package mdoc.internal.minc

final case class RunOptions()
object RunOptions {
  val default = RunOptions()
  implicit lazy val surface = metaconfig.generic.deriveSurface[RunOptions]
  implicit lazy val codec = metaconfig.generic.deriveCodec[RunOptions](default)
}
