package tests.markdown

import mdoc.internal.cli.Settings
import java.nio.file.Files
import scala.meta.io.AbsolutePath

class BloopCompilerSuite extends BaseMarkdownSuite {
  override def baseSettings: Settings = super.baseSettings.copy(compiler = "bloop")
  check(
    "basic",
    """
      |```scala mdoc
      |val x = 1
      |println("42")
      |```
    """.stripMargin,
    """|```scala
       |val x = 1
       |// x: Int = 1
       |println("42")
       |// 42
       |```
       |""".stripMargin
  )

}
