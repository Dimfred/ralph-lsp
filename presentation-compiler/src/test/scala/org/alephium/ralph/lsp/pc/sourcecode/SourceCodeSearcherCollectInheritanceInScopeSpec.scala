package org.alephium.ralph.lsp.pc.sourcecode

import org.alephium.ralph.lsp.access.compiler.CompilerAccess
import org.alephium.ralph.lsp.access.compiler.ast.Tree
import org.alephium.ralph.lsp.access.file.FileAccess
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.EitherValues._

import scala.collection.immutable.ArraySeq

class SourceCodeSearcherCollectInheritanceInScopeSpec extends AnyWordSpec with Matchers {

  implicit val file: FileAccess = FileAccess.disk
  implicit val compiler: CompilerAccess = CompilerAccess.ralphc

  "return empty" when {
    "input source-code is empty" in {
      val parsed =
        TestSourceCode.genParsed(
          """
            |Contract MyContract() {
            |  fn function1() -> () {}
            |}
            |""".stripMargin
        ).sample.get.asInstanceOf[SourceCodeState.Parsed]

      val tree =
        parsed.ast.statements.head.asInstanceOf[Tree.Source]

      SourceCodeSearcher.collectInheritanceInScope(
        source = tree,
        allSource = ArraySeq.empty
      ) shouldBe empty

      TestSourceCode deleteIfExists parsed
    }
  }

  "collect single parent implementations" when {
    def doTest(code: String) = {
      val parsed =
        TestSourceCode
          .genParsed(code)
          .sample
          .get
          .asInstanceOf[SourceCodeState.Parsed]

      // first statement is Parent()
      val parent = parsed.ast.statements.head.asInstanceOf[Tree.Source]
      parent.ast.merge.name shouldBe "Parent"

      // second statement is Child()
      val child = parsed.ast.statements.last.asInstanceOf[Tree.Source]
      child.ast.merge.name shouldBe "Child"

      // expect parent to be returned
      val expected =
        SourceTreeInScope(
          tree = parent,
          parsed = parsed
        )

      val actual =
        SourceCodeSearcher.collectInheritanceInScope(
          source = child,
          allSource = ArraySeq(parsed)
        )

      actual should contain only expected

      TestSourceCode deleteIfExists parsed
    }

    "parent is an Abstract Contract" in {
      doTest {
        """
          |Abstract Contract Parent() { }
          |
          |Contract Child() extends Parent() {
          |  fn function1() -> () {}
          |}
          |""".stripMargin
      }
    }

    "parent is an Interface" in {
      doTest {
        """
          |Interface Parent {
          |  pub fn parent() -> U256
          |}
          |
          |Contract Child() implements Parent {
          |  pub fn parent() -> U256 {
          |    return 1
          |  }
          |}
          |""".stripMargin
      }
    }
  }

  "collect deep inheritance" when {
    "it also contains cyclic and duplicate inheritance" in {
      // file1 contains the Child() contract for which the parents are collected.
      val file1 =
        TestSourceCode
          .genParsed(
            """
              |Abstract Contract Parent2() extends Parent4(), Parent6() implements Parent1 { }
              |
              |// Interface is implemented
              |Interface Parent1 {
              |  pub fn parent() -> U256
              |}
              |
              |// Parent3 is not extends by Child(), it should not be in the result
              |Abstract Contract Parent3() extends Parent4(), Parent4() { }
              |
              |// The child parents to collect
              |Contract Child() extends Parent2(), Child() implements Parent1 {
              |  pub fn parent() -> U256 {
              |    return 1
              |  }
              |}
              |""".stripMargin
          )
          .sample
          .get
          .asInstanceOf[SourceCodeState.Parsed]

      // file2 contains all other abstract contracts
      val file2 =
        TestSourceCode
          .genParsed(
            """
              |Abstract Contract Parent6() extends Parent4() { }
              |
              |Abstract Contract Parent5() extends Parent4(), Parent5() { }
              |
              |Abstract Contract Parent4() extends Parent5(), Parent6(), Parent4() { }
              |""".stripMargin
          )
          .sample
          .get
          .asInstanceOf[SourceCodeState.Parsed]

      // collect all tree from file1
      val treesFromFile1 =
        file1.ast.statements.map(_.asInstanceOf[Tree.Source])

      // collect all tree from file2
      val treesFromFile2 =
        file2.ast.statements.map(_.asInstanceOf[Tree.Source])

      // the last statement in file1 is Child()
      val child = treesFromFile1.last
      child.ast.merge.name shouldBe "Child"

      // expect parents to be returned excluding Parent3() and Child()
      val expectedTreesFromFile1 =
        treesFromFile1
          .filterNot {
            tree =>
              tree.ast.merge.name == "Parent3" || tree.ast.merge.name == "Child"
          }
          .map {
            parent =>
              SourceTreeInScope(
                tree = parent,
                parsed = file1 // file1 is in scope
              )
          }

      val expectedTreesFromFile2 =
        treesFromFile2
          .map {
            parent =>
              SourceTreeInScope(
                tree = parent,
                parsed = file2 // file2 is in scope
              )
          }

      // collect all parent trees to expect
      val expectedTrees =
        expectedTreesFromFile1 ++ expectedTreesFromFile2

      // actual trees returned
      val actual =
        SourceCodeSearcher.collectInheritanceInScope(
          source = child,
          allSource = ArraySeq(file1, file2)
        )

      actual should contain theSameElementsAs expectedTrees

      // Double check: Also assert the names of the parents.
      val parentNames = actual.map(_.tree.ast.left.value.name)
      // Note: Parent3 and Child are not included.
      parentNames should contain only("Parent1", "Parent2", "Parent4", "Parent5", "Parent6")

      TestSourceCode deleteAllIfExists Array(file1, file2)
    }
  }

}