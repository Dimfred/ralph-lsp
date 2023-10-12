package org.alephium.ralph.lsp.pc.sourcecode

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import fastparse._
import fastparse.MultiLineWhitespace._

import org.alephium.ralph.lsp.compiler.message.SourceIndex

class ImportHandlerSpec extends AnyWordSpec with Matchers {

  "parse imports " in {
    def p(code: String, success: Boolean) = {
      val res = fastparse.parse(code ++ "\n" ++ "Contract Test(id:U256){}", ImportHandler.Lexer.imports(_))

      if(success)  {
        res.get.value.toList.size shouldBe 2
      }else {
        //Here we only try to parse import, we don't try to parse the comtract, as it's done by the CompileAccess,
        //so an empty list of contract is returned.
        res.get.value shouldBe List.empty
      }
    }

    def s(code: String) =
      p(code, true)

    def f(code: String) =
      p(code, false)

    s("""
      import "std/nft_interface"
      import "std/fungible_token_interface"
      """)

    s("""
      import "std/fungible_token_interface"


      import "std/nft_interface"



      """)

    f("""
      mport "std/fungible_token_interface"
      """)

    f("""
      import std/fungible_token_interface"
      """)

    f("""
      import std/fungible_token_interface"
      """)
  }

  "extractStdImports" should {
    def p(code: String) = {
      val res = ImportHandler.extractStdImports(code ++ "\n" ++ "Contract Test(id:U256){}")
      res.map(_.imports.keys) shouldBe Right(Set("std/nft_interface", "std/fungible_token_interface"))
    }

    "extract valid imports" in {
      p("""
        import "std/nft_interface"
        import "std/fungible_token_interface"
        """)

      p("""
        import

        "std/nft_interface"
        import      "std/fungible_token_interface"


        """)

      p("""
        import "std/nft_interface.ral"
        import "std/fungible_token_interface.ral"
        """)
    }

    "ignore comments" in {
      p("""
        //comment
        import "std/nft_interface"
        import      "std/fungible_token_interface"
        """")

      p("""
        //import "std/nft_interface"
        import "std/nft_interface"
        import      "std/fungible_token_interface"
        """")

      p("""
        // comment before
        import
        // comment between
        "std/nft_interface"
        import "std/fungible_token_interface"
        // comment after
        """")
    }

    "find all import errors" in {

      def p(code: String, indexes: Seq[SourceIndex]) = {
        val res = ImportHandler.extractStdImports(code ++ "\n" ++ "Contract Test(id:U256){}")

        res.isLeft shouldBe true

        res.left.map(_.zip(indexes).map { case (error, index) =>
          error.index shouldBe index
        })
      }

      p("""|import "std/nt_interface"
        |
        |import "std/"
        """.stripMargin, Seq(SourceIndex(8, 16), SourceIndex(35,4)))

      p("""import "td/nft"""", Seq(SourceIndex(8, 6)))
      p("""import "td /nft"""", Seq(SourceIndex(8, 7)))
      p("""import "   td/nft  """", Seq(SourceIndex(8, 11)))
      p("""import "td / nft"""", Seq(SourceIndex(8, 8)))
    }

    "extract import even with some contract or any thing between imports" in {
      //We only care about imports herer, contracts are parse latter by the `CompilerAccess`
      p("""
        import "std/nft_interface"
        Contract Foo(id:U256){}
        import "std/fungible_token_interface"
        """)

      p("""
        Contract Foo(id:U256){}
        Contract Boo(id:U256){}
        import "std/nft_interface"
        import "std/fungible_token_interface"
        """)

      p("""
        import "std/nft_interface"
        Some random stuff
        import "std/fungible_token_interface"
        """)

      p("""
        Some random stuff
        import "std/nft_interface"
        Some random stuff
        import "std/fungible_token_interface"
        """)
    }
  }
}
