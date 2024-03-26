package org.alephium.ralph.lsp.pc.search.gotodef

import org.alephium.ralph.Ast
import org.alephium.ralph.lsp.access.compiler.ast.Tree
import org.alephium.ralph.lsp.access.compiler.ast.node.Node
import org.alephium.ralph.lsp.access.compiler.message.SourceIndexExtra.SourceIndexExtension
import org.alephium.ralph.lsp.pc.search.gotodef.data.GoToLocation
import org.alephium.ralph.lsp.pc.sourcecode.SourceCodeState

private object GoToSource {

  /**
   * Navigates to the definition of a token in the source code.
   *
   * @param cursorIndex The index of the token clicked by the user.
   * @param sourceCode  The requested source file.
   * @param sourceAST   Parsed AST of the requested source file's code.
   * @return An array sequence containing the target go-to location(s).
   */
  def goTo(cursorIndex: Int,
           sourceCode: SourceCodeState.Parsed,
           sourceAST: Tree.Source): Iterator[GoToLocation] = {
    val goToResult =
      goTo(
        cursorIndex = cursorIndex,
        source = sourceAST
      )

    // covert go-to node to GoToLocation
    GoToLocation(
      sourceCode = sourceCode,
      asts = goToResult
    )
  }

  /**
   * Navigates to the definition of a token in the source code.
   *
   * @param cursorIndex The index of the token clicked by the user.
   * @param source      The source tree to navigate within.
   * @return An iterator over the positioned AST elements found.
   */
  private def goTo(cursorIndex: Int,
                   source: Tree.Source): Iterator[Ast.Positioned] =
    source.rootNode.findLast(_.sourceIndex.exists(_ contains cursorIndex)) match { // find the node closest to this source-index
      case Some(closest) =>
        closest match {
          case identNode @ Node(ident: Ast.Ident, _) =>
            // the clicked/closest node is an ident
            GoToIdent.goTo(
              identNode = identNode,
              ident = ident,
              source = source
            )

          case funcIdNode @ Node(funcId: Ast.FuncId, _) =>
            // the clicked/closest node is functionId
            GoToFuncId.goTo(
              funcIdNode = funcIdNode,
              funcId = funcId,
              source = source
            )

          case typIdNode @ Node(typeId: Ast.TypeId, _) =>
            // the clicked/closest node is TypeId
            GoToTypeId.goTo(
              identNode = typIdNode,
              typeId = typeId,
              source = source
            )

          case _ =>
            Iterator.empty
        }

      case None =>
        Iterator.empty
    }

}
