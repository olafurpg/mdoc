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
      |/hello1.sc
      |val message: String = 42
      |/hello2.sc
      |import $file.hello1
      |val number: Int = ""
      |/readme.md
      |```scala mdoc
      |import $file.hello2
      |val something: Int = ""
      |println(hello2.number)
      |```
      |""".stripMargin,
    "",
    expectedExitCode = 1,
    includeOutputPath = includeOutputPath,
    onStdout = { stdout =>
      assertNoDiff(
        stdout,
        """|info: Compiling 3 files to <output>
           |error: <input>/hello1.sc:1:23: type mismatch;
           | found   : Int(42)
           | required: String
           |val message: String = 42
           |                      ^^
           |error: <input>/hello2.sc:2:19: type mismatch;
           | found   : String("")
           | required: Int
           |val number: Int = ""
           |                  ^^
           |error: <input>/readme.md:3:22: type mismatch;
           | found   : String("")
           | required: Int
           |val something: Int = ""
           |                     ^^
           |""".stripMargin
      )
    }
  )

  checkCli(
    "conflicting-package",
    """
      |/hello0.sc
      |val zero = 0
      |/inner/hello1.sc
      |val one = 1
      |/inner/hello2.sc
      |import $file.hello1
      |import $file.^.hello0
      |val two = hello1.one + 1 + hello0.zero
      |/hello3.sc
      |import $file.hello0
      |import $file.inner.hello1
      |import $file.inner.hello2
      |val three = hello0.zero + hello1.one + hello2.two
      |/readme.md
      |```scala mdoc
      |import $file.hello3
      |println(hello3.three)
      |```
      |""".stripMargin,
    """|/readme.md
       |```scala
       |import $file.hello3
       |println(hello3.three)
       |// 3
       |```
       |""".stripMargin,
    includeOutputPath = includeOutputPath
  )

  checkCli(
    "importees",
    """
      |/hello0.sc
      |val zero = 0
      |/hello1.sc
      |val one = 1
      |/hello2.sc
      |val two = 2
      |/readme.md
      |```scala mdoc
      |import $file.{ hello0, hello1 => h1, hello2 => _, _ }
      |println(hello3.three)
      |```
      |""".stripMargin,
    "",
    expectedExitCode = 1,
    includeOutputPath = includeOutputPath,
    onStdout = { stdout =>
      assertNoDiff(
        stdout,
        // NOTE(olafur): feel free to update the expected output here if implement
        // support for repeated importee syntax. It's not a use-case I H
        """|info: Compiling 4 files to <output>
           |error: <input>/readme.md:2:8: unsupported syntax. To fix this problem, use regular `import $file.path` imports without curly braces.
           |import $file.{ hello0, hello1 => h1, hello2 => _, _ }
           |       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           |""".stripMargin
      )
    }
  )

}
