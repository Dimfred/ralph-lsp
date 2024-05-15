// Copyright 2024 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see http://www.gnu.org/licenses/.

package org.alephium.ralph.lsp.pc.search.completion

import org.alephium.ralph.lsp.pc.sourcecode.SourceLocation
import org.alephium.ralph.lsp.pc.workspace.WorkspaceState
import org.alephium.ralph.Ast
import org.alephium.ralph.lsp.access.compiler.ast.node.Node
import org.alephium.ralph.lsp.access.compiler.message.SourceIndexExtra.SourceIndexExtension

object SourceCodeCompleter {

  /**
   * Provides code completion suggestions at the given cursor index within the source code.
   *
   * @param cursorIndex The index representing the cursor position in the source code.
   * @param sourceCode  The source code where the completion is requested.
   * @param workspace   The workspace state containing the source code.
   * @return An iterator over code completion suggestions.
   */
  def complete(
      cursorIndex: Int,
      sourceCode: SourceLocation.Code,
      workspace: WorkspaceState.IsSourceAware): Iterator[Suggestion] =
    sourceCode.tree.rootNode.findLast(_.sourceIndex.exists(_ contains cursorIndex)) match { // find the node closest to this source-index
      case Some(closest) =>
        closest match {
          case functionNode @ Node(function: Ast.FuncDef[_], _) =>
            FunctionBodyCompleter.suggest(
              cursorIndex = cursorIndex,
              functionNode = functionNode.upcast(function),
              sourceCode = sourceCode,
              workspace = workspace
            )

          case _ =>
            Iterator.empty
        }

      case None =>
        Iterator.empty
    }

}