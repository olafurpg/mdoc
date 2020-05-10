package tests.imports

import tests.markdown.BaseMarkdownSuite
import tests.cli.BaseCliSuite
import scala.meta.io.RelativePath

class FileSuite extends BaseCliSuite {

  val includeOutputPath: RelativePath => Boolean = { path => path.toNIO.endsWith("readme.md") }
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
    """
      |/readme.md
      |```scala
      |import $file.hello
      |println(hello.message)
      |// Hello world!
      |```
      |""".stripMargin,
    includeOutputPath = includeOutputPath
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
    """
      |/readme.md
      |```scala
      |import $file.inner.hello
      |println(hello.message)
      |// hello world
      |```
      |""".stripMargin,
    includeOutputPath = includeOutputPath
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
      s"""|/$inner/readme.md
          |```scala
          |import $$file.$caret.hello
          |println(hello.message)
          |// hello world
          |```
          |""".stripMargin,
      includeOutputPath = includeOutputPath
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
    """|/readme.md
       |```scala
       |import $file.hello2, $file.hello3
       |println(s"${hello2.first} ${hello3.second}")
       |// hello world
       |```
       |""".stripMargin,
    includeOutputPath = includeOutputPath
  )

  checkCli(
    "cycles",
    """
      |/hello1.sc
      |import $file.hello2
      |val first = hello2.first
      |/hello2.sc
      |import $file.hello1
      |val first = hello1.first
      |/readme.md
      |```scala mdoc
      |import $file.hello1
      |println(s"${hello1.first} world")
      |```
      |""".stripMargin,
    "",
    expectedExitCode = 1,
    includeOutputPath = includeOutputPath,
    onStdout = { stdout =>
      assertNoDiff(
        stdout,
        """|info: Compiling 3 files to <output>
           |error: <input>/readme.md:2:14: illegal cyclic dependency. To fix this problem, refactor the code so that no transitive $file imports end up depending on the original file.
           | -- root       --> <input>/readme.md:1
           | -- depends on --> <input>/hello1.sc:0
           | -- depends on --> <input>/hello2.sc:0
           | -- cycle      --> <input>/hello1.sc
           |import $file.hello1
           |             ^^^^^^
           |""".stripMargin
      )
    }
  )

  checkCli(
    "compile-error",
    """
      |/hello.sc
      |val number: Int = ""
      |/readme.md
      |```scala mdoc
      |import $file.hello
      |println(hello.number)
      |```
      |""".stripMargin,
    "",
    expectedExitCode = 1,
    includeOutputPath = includeOutputPath,
    onStdout = { stdout =>
      assertNoDiff(
        stdout,
        """|info: Compiling 2 files to <output>
           |error: <input>/hello.sc:1:19: type mismatch;
           | found   : String("")
           | required: Int
           |val number: Int = ""
           |                  ^
           |""".stripMargin
      )
    }
  )

}
