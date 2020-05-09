package tests.imports

import tests.markdown.BaseMarkdownSuite
import tests.cli.BaseCliSuite

class FileSuite extends BaseCliSuite {
  checkCli(
    "basic",
    """
      |/hello.sc
      |val message = "Hello world!"
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
    "outer",
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

  1.to(3).foreach { i =>
    val caret = "^" * i
    val inner = 1.to(i).map(_ => "inner").mkString("/")
    checkCli(
      inner,
      s"""
         |/hello.sc
         |val message = "hello world"
         |/$inner/readme.md
         |```scala mdoc
         |import $$file.$caret.hello
         |println(hello.message)
         |```
         |""".stripMargin,
      s"""|/hello.sc
          |val message = "hello world"
          |/$inner/readme.md
          |```scala
          |import $$file.$caret.hello
          |println(hello.message)
          |// hello world
          |```
          |""".stripMargin
    )
  }

  checkCli(
    "nested",
    """
      |/hello1.sc
      |val first = "hello"
      |val second = "world"
      |/hello2.sc
      |import $file.hello1
      |val first = hello1.first
      |/hello3.sc
      |import $file.hello1
      |val second = hello1.second
      |/readme.md
      |```scala mdoc
      |import $file.hello2, $file.hello3
      |println(s"${hello2.first} ${hello3.second}")
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

}
