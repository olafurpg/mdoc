package tests

import munit.FunSuite
import munit.Location
import tests.markdown.Compat
import breeze.numerics.exp

class BaseSuite extends FunSuite {
  def postProcessObtained: Map[String, String => String] = Map.empty
  def postProcessExpected: Map[String, String => String] = Map.empty
  override def assertNoDiff(obtained: String, expected: String, clue: => Any)(
      implicit loc: Location
  ): Unit = {
    super.assertNoDiff(
      Compat(obtained, Map.empty, postProcessObtained),
      Compat(expected, Map.empty, postProcessExpected),
      clue
    )
  }
  object OnlyScala213 extends munit.Tag("OnlyScala213")
  override def munitTestTransforms: List[TestTransform] = super.munitTestTransforms ++ List(
    new TestTransform(OnlyScala213.value, { test =>
      if (test.tags(OnlyScala213) && mdoc.internal.BuildInfo.scalaBinaryVersion != "2.13")
        test.tag(munit.Ignore)
      else test
    })
  )
}
