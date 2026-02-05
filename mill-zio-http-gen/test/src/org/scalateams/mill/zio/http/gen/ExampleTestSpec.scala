package org.scalateams.mill.zio.http.gen

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import mill.testkit.ExampleTester
import os.{Path, RelPath}

class ExampleTestSpec extends AnyWordSpec with Matchers {

  def test(project: RelPath): Path = {
    val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
    ExampleTester.run(
      daemonMode = true,
      workspaceSourcePath = resourceFolder / project,
      millExecutable = os.Path(sys.env("MILL_EXECUTABLE_PATH")),
    )
  }

  "examples" should {
    "basic usage" in {
      test(RelPath("example/basic"))
    }
  }
}
