package com.easybox.app.domain.international

import com.easybox.app.domain.chess.AIDifficulty
import com.easybox.app.domain.international.model.*
import kotlin.math.max
import kotlin.math.min

class InternationalChessAI(private val difficulty: AIDifficulty = AIDifficulty.MEDIUM) {

    private val pieceValues = mapOf(
        IType.KING to 10000,
        IType.QUEEN to 900,
        IType.ROOK to 500,
        IType.BISHOP to 330,
        IType.KNIGHT to 320,
        IType.PAWN to 100
    )

    // Piece-square tables for positional evaluation (White perspective, flip for Black)
    private val pawnTable = arrayOf(
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0),
        intArrayOf(50, 50, 50, 50, 50, 50, 50, 50),
        intArrayOf(10, 10, 20, 30, 30, 20, 10, 10),
        intArrayOf(5,  5, 10, 25, 25, 10,  5,  5),
        intArrayOf(0,  0,  0, 20, 20,  0,  0,  0),
        intArrayOf(5, -5,-10,  0,  0,-10, -5,  5),
        intArrayOf(5, 10, 10,-20,-20, 10, 10,  5),
        intArrayOf(0,  0,  0,  0,  0,  0,  0,  0)
    )

    fun getBestMove(engine: InternationalChessEngine): IMove? {
        val moves = engine.getAllValidMoves()
        if (moves.isEmpty()) return null

        val ordered = moves.sortedByDescending { move ->
            (move.captured?.let { pieceValues[it.type] ?: 0 } ?: 0) * 10 +
            (move.promotion?.let { 500 } ?: 0)
        }

        var bestMove = ordered.first()
        var bestScore = Int.MIN_VALUE
        val alpha = Int.MIN_VALUE + 100000
        val beta = Int.MAX_VALUE - 100000
        val aiColor = engine.currentPlayer

        for (move in ordered) {
            val clonedBoard = engine.getBoard().clone()
            // Handle promotion in simulation
            if (move.promotion != null) {
                clonedBoard.grid[move.to.row][move.to.col] =
                    IPiece(move.promotion, clonedBoard.grid[move.from.row][move.from.col]!!.color)
            } else {
                clonedBoard.grid[move.to.row][move.to.col] =
                    clonedBoard.grid[move.from.row][move.from.col]
            }
            clonedBoard.grid[move.from.row][move.from.col] = null

            val opponent = if (aiColor == IColor.WHITE) IColor.BLACK else IColor.WHITE

            // Simple engine simulation without castling/en passant state for speed
            val score = minimax(
                board = clonedBoard,
                depth = difficulty.depth - 1,
                alpha = alpha,
                beta = beta,
                maximizingPlayer = false,
                aiColor = aiColor,
                currentPlayer = opponent
            )

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return bestMove
    }

    private fun minimax(
        board: IBoard,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximizingPlayer: Boolean,
        aiColor: IColor,
        currentPlayer: IColor
    ): Int {
        if (depth == 0) return evaluate(board, aiColor)

        val opponent = if (currentPlayer == IColor.WHITE) IColor.BLACK else IColor.WHITE

        if (maximizingPlayer) {
            var a = alpha
            var maxEval = Int.MIN_VALUE + 100000
            val moves = generateAllMoves(board, currentPlayer)
            if (moves.isEmpty()) {
                return if (isInCheckStatic(board, currentPlayer)) -99999 + (difficulty.depth - depth) else 0
            }

            for (move in moves) {
                val newBoard = applyMove(board, move)
                val eval = minimax(newBoard, depth - 1, a, beta, false, aiColor, opponent)
                maxEval = max(maxEval, eval)
                a = max(a, eval)
                if (beta <= a) break
            }
            return maxEval
        } else {
            var b = beta
            var minEval = Int.MAX_VALUE - 100000
            val moves = generateAllMoves(board, currentPlayer)
            if (moves.isEmpty()) {
                return if (isInCheckStatic(board, currentPlayer)) 99999 - (difficulty.depth - depth) else 0
            }

            for (move in moves) {
                val newBoard = applyMove(board, move)
                val eval = minimax(newBoard, depth - 1, alpha, b, true, aiColor, opponent)
                minEval = min(minEval, eval)
                b = min(b, eval)
                if (b <= alpha) break
            }
            return minEval
        }
    }

    private fun applyMove(board: IBoard, move: IMove): IBoard {
        val newBoard = board.clone()
        if (move.promotion != null) {
            newBoard.grid[move.to.row][move.to.col] =
                IPiece(move.promotion, newBoard.grid[move.from.row][move.from.col]!!.color)
        } else {
            newBoard.grid[move.to.row][move.to.col] = newBoard.grid[move.from.row][move.from.col]
        }
        newBoard.grid[move.from.row][move.from.col] = null
        return newBoard
    }

    private fun evaluate(board: IBoard, aiColor: IColor): Int {
        var score = 0
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board.grid[row][col] ?: continue
                val value = pieceValues[piece.type] ?: 0
                val posBonus = getPositionalBonus(piece, row, col)
                val totalValue = value + posBonus

                if (piece.color == aiColor) score += totalValue
                else score -= totalValue
            }
        }
        return score
    }

    private fun getPositionalBonus(piece: IPiece, row: Int, col: Int): Int {
        if (piece.type == IType.PAWN) {
            val tableRow = if (piece.color == IColor.WHITE) row else 7 - row
            return pawnTable[tableRow][col]
        }
        return 0
    }

    private fun generateAllMoves(board: IBoard, color: IColor): List<IMove> {
        val moves = mutableListOf<IMove>()
        val tempEngine = InternationalChessEngine()
        tempEngine.setBoard(board.clone(), color)

        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != color) continue
                val from = IPosition(row, col)
                val targets = tempEngine.getValidMoves(from)
                for (to in targets) {
                    val promotion = if (piece.type == IType.PAWN && (to.row == 0 || to.row == 7))
                        IType.QUEEN else null
                    moves.add(IMove(from, to, board.pieceAt(to), promotion))
                }
            }
        }

        return moves.sortedByDescending { move ->
            (move.captured?.let { pieceValues[it.type] ?: 0 } ?: 0) +
            (move.promotion?.let { 500 } ?: 0)
        }
    }

    private fun isInCheckStatic(board: IBoard, color: IColor): Boolean {
        val kingPos = findKing(board, color) ?: return true
        val opponent = if (color == IColor.WHITE) IColor.BLACK else IColor.WHITE
        val tempEngine = InternationalChessEngine()
        tempEngine.setBoard(board.clone(), opponent)

        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != opponent) continue
                val targets = tempEngine.getValidMoves(IPosition(row, col))
                if (kingPos in targets) return true
            }
        }
        return false
    }

    private fun findKing(board: IBoard, color: IColor): IPosition? {
        for (row in 0..7) {
            for (col in 0..7) {
                val p = board.grid[row][col]
                if (p != null && p.type == IType.KING && p.color == color) {
                    return IPosition(row, col)
                }
            }
        }
        return null
    }
}
