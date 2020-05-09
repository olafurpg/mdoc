package tests.imports

import tests.markdown.BaseMarkdownSuite
import tests.cli.BaseCliSuite

class FileSuite extends BaseCliSuite {
  checkCli(
    "basic",
    """
      |/hello.sc
      |val message = "hello world"
      |/readme.md
      |```scala mdoc
      |import $file.hello
      |println(hello.message)
      |```
      |""".stripMargin,
    """|/hello.sc
       |val message = "Hello world!"
       |/readme.md
       |```scala
       |import $file.hello
       |println(hello.message)
       |// Hello world!
       |```
       |""".stripMargin
  )

  checkCli(
    "inner",
    """
      |/inner/hello.sc
      |val message = "hello world"
      |/readme.md
      |```scala mdoc
      |import $file.inner.hello
      |println(hello.message)
      |```
      |""".stripMargin,
    """|/inner/hello.sc
       |val message = "hello world"
       |/readme.md
       |```scala
       |import $file.inner.hello
       |println(hello.message)
       |// hello world
       |```
       |""".stripMargin
  )

  checkCli(
    "outer",
    """
      |/hello.sc
      |val message = "hello world"
      |/inner/readme.md
      |```scala mdoc
      |import $file.^.hello
      |println(hello.message)
      |```
      |""".stripMargin,
    """|/hello.sc
       |val message = "hello world"
       |/inner/readme.md
       |```scala
       |import $file.^.hello
       |println(hello.message)
       |// hello world
       |```
       |""".stripMargin
  )

}
