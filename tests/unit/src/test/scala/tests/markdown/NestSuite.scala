package tests.markdown

class NestSuite extends BaseMarkdownSuite {

  override def postProcessExpected: Map[String, String => String] = Map(
    "2.13" -> { old => old.replace("of type => Int", "of type Int") }
  )
  check(
    "redefine-val",
    """
      |```scala mdoc
      |val x = 1
      |val y = 1
      |println(x + y)
      |```
      |```scala mdoc:nest
      |val x = "x"
      |println(x + y)
      |```
      |```scala mdoc
      |println(x + y)
      |```
    """.stripMargin,
    """|
       |```scala
       |val x = 1
       |// x: Int = 1
       |val y = 1
       |// y: Int = 1
       |println(x + y)
       |// 2
       |```
       |```scala
       |val x = "x"
       |// x: String = "x"
       |println(x + y)
       |// x1
       |```
       |```scala
       |println(x + y)
       |// x1
       |```
       |""".stripMargin
  )

  check(
    "redefine-class",
    """
      |```scala mdoc
      |case class User(name: String)
      |val susan = User("Susan")
      |```
      |```scala mdoc:nest
      |case class User(name: String, age: Int)
      |val hakon = User("Hakon", 42)
      |susan
      |```
    """.stripMargin,
    """|
       |```scala
       |case class User(name: String)
       |val susan = User("Susan")
       |// susan: User = User("Susan")
       |```
       |```scala
       |case class User(name: String, age: Int)
       |val hakon = User("Hakon", 42)
       |// hakon: User = User("Hakon", 42)
       |susan
       |// res0: App.this.type.User = User("Susan")
       |```
       |""".stripMargin
  )

  check(
    "multi-nest",
    """
      |```scala mdoc
      |val x = 1
      |val a = 1
      |```
      |```scala mdoc:nest
      |val y = "y"
      |val b = 2
      |```
      |```scala mdoc:nest
      |val z = List(1)
      |val c = 3
      |```
      |```scala mdoc
      |println(x)
      |println(y)
      |println(z)
      |```
    """.stripMargin,
    """|
       |```scala
       |val x = 1
       |// x: Int = 1
       |val a = 1
       |// a: Int = 1
       |```
       |```scala
       |val y = "y"
       |// y: String = "y"
       |val b = 2
       |// b: Int = 2
       |```
       |```scala
       |val z = List(1)
       |// z: List[Int] = List(1)
       |val c = 3
       |// c: Int = 3
       |```
       |```scala
       |println(x)
       |// 1
       |println(y)
       |// y
       |println(z)
       |// List(1)
       |```
       |""".stripMargin
  )

  check(
    "implicit-ok",
    """
      |```scala mdoc
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc
      |implicitly[Int]
      |```
    """.stripMargin,
    """|
       |```scala
       |implicit val x = 1
       |// x: Int = 1
       |```
       |```scala
       |implicit val x = 1
       |// x: Int = 1
       |```
       |```scala
       |implicitly[Int]
       |// res0: Int = 1
       |```
       |
       |""".stripMargin
  )

  checkError(
    "reset",
    """
      |```scala mdoc
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc:reset
      |implicitly[Int]
      |```
    """.stripMargin,
    """|error: reset.md:9:1: could not find implicit value for parameter e: Int
       |implicitly[Int]
       |^^^^^^^^^^^^^^^
       |""".stripMargin
  )

  checkError(
    "multi-reset",
    """
      |```scala mdoc
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc:reset
      |implicitly[Int]
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val x = 1
      |```
      |```scala mdoc:reset
      |implicitly[Int]
      |```
    """.stripMargin,
    """|error: multi-reset.md:15:1: could not find implicit value for parameter e: Int
       |implicitly[Int]
       |^^^^^^^^^^^^^^^
       |error: multi-reset.md:27:1: could not find implicit value for parameter e: Int
       |implicitly[Int]
       |^^^^^^^^^^^^^^^
       |""".stripMargin
  )

  checkError(
    "implicit-nok",
    """
      |```scala mdoc
      |implicit val x = 1
      |```
      |```scala mdoc:nest
      |implicit val y = 1
      |```
      |```scala mdoc
      |implicitly[Int]
      |```
    """.stripMargin,
    """|error: implicit-nok.md:9:1: ambiguous implicit values:
       | both value x in class App of type => Int
       | and value y of type Int
       | match expected type Int
       |implicitly[Int]
       |^^^^^^^^^^^^^^^
       |""".stripMargin
  )

  checkError(
    "anyval-nok",
    """
      |```scala mdoc:reset-object
      |val x = 1
      |```
      |```scala mdoc:nest
      |class Foo(val x: Int) extends AnyVal
      |```
    """.stripMargin,
    """|error: anyval-nok.md:6:1: value class may not be a local class
       |class Foo(val x: Int) extends AnyVal
       |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
       |""".stripMargin
  )

  checkError(
    "stacktrace",
    """
      |```scala mdoc
      |val x = 1
      |```
      |```scala mdoc:nest
      |val x = 2
      |```
      |```scala mdoc:nest
      |val x = 3
      |```
      |```scala mdoc:nest
      |val x = 4
      |def boom(cond: Boolean) = if (!cond) throw new IllegalArgumentException()
      |boom(x > 4)
      |```
    """.stripMargin,
    """|error: stacktrace.md:14:1: null
       |boom(x > 4)
       |^^^^^^^^^^^
       |java.lang.IllegalArgumentException
       |	at repl.Session$App.boom$1(stacktrace.md:32)
       |	at repl.Session$App.<init>(stacktrace.md:35)
       |	at repl.Session$.app(stacktrace.md:3)
       |""".stripMargin
  )

  check(
    "fail",
    """
      |```scala mdoc
      |case class Foo(i: Int)
      |```
      |
      |```scala mdoc:nest:fail
      |case class Foo(i: Int) { val x = y }
      |```
    """.stripMargin,
    """|
       |```scala
       |case class Foo(i: Int)
       |```
       |
       |```scala
       |case class Foo(i: Int) { val x = y }
       |// error: not found: value y
       |// case class Foo(i: Int) { val x = y }
       |//                                  ^
       |```
    """.stripMargin
  )

}
