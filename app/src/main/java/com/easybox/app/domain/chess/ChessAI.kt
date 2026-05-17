package com.easybox.app.domain.chess

import com.easybox.app.domain.chess.model.*
import kotlin.math.max
import kotlin.math.min

enum class AIDifficulty(val depth: Int, val label: String) {
    EASY(1, "简单"),
    MEDIUM(3, "中等"),
    HARD(5, "困难")
}

class ChineseChessAI(private val difficulty: AIDifficulty = AIDifficulty.MEDIUM) {

    private var engine = ChineseChessEngine()

    private val pieceValues = mapOf(
        PieceType.KING to 10000,
        PieceType.ROOK to 600,
        PieceType.CANNON to 300,
        PieceType.KNIGHT to 270,
        PieceType.ELEPHANT to 120,
        PieceType.ADVISOR to 120,
        PieceType.PAWN to 30
    )

    // Positional bonus for pawns that crossed the river
    private val pawnCrossedBonus = 50

    fun getBestMove(engine: ChineseChessEngine): Move? {
        this.engine = engine
        val moves = engine.getAllValidMoves()
        if (moves.isEmpty()) return null

        // Move ordering: captures first, then by piece value
        val ordered = moves.sortedByDescending { move ->
            val captured = engine.getBoard().pieceAt(move.to)
            (captured?.let { pieceValues[it.type] ?: 0 } ?: 0) * 10 +
            (pieceValues[engine.getBoard().pieceAt(move.from)?.type] ?: 0)
        }

        var bestMove = ordered.first()
        var bestScore = Int.MIN_VALUE
        val alpha = Int.MIN_VALUE + 100000
        val beta = Int.MAX_VALUE - 100000
        val aiColor = engine.currentPlayer

        for (move in ordered) {
            val clonedBoard = engine.getBoard().clone()
            clonedBoard.grid[move.to.row][move.to.col] = clonedBoard.grid[move.from.row][move.from.col]
            clonedBoard.grid[move.from.row][move.from.col] = null

            val opponent = if (aiColor == PieceColor.RED) PieceColor.BLACK else PieceColor.RED
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
        board: Board,
        depth: Int,
        alpha: Int,
        beta: Int,
        maximizingPlayer: Boolean,
        aiColor: PieceColor,
        currentPlayer: PieceColor
    ): Int {
        if (depth == 0) {
            return evaluate(board, aiColor)
        }

        val tempEngine = ChineseChessEngine()
        val opponent = if (currentPlayer == PieceColor.RED) PieceColor.BLACK else PieceColor.RED

        if (maximizingPlayer) {
            var a = alpha
            var maxEval = Int.MIN_VALUE + 100000
            val moves = generateAllMoves(board, currentPlayer)
            if (moves.isEmpty()) return if (isInCheckStatic(board, currentPlayer)) -99999 + (difficulty.depth - depth) else 0

            for (move in moves) {
                val newBoard = board.clone()
                newBoard.grid[move.to.row][move.to.col] = newBoard.grid[move.from.row][move.from.col]
                newBoard.grid[move.from.row][move.from.col] = null

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
            if (moves.isEmpty()) return if (isInCheckStatic(board, currentPlayer)) 99999 - (difficulty.depth - depth) else 0

            for (move in moves) {
                val newBoard = board.clone()
                newBoard.grid[move.to.row][move.to.col] = newBoard.grid[move.from.row][move.from.col]
                newBoard.grid[move.from.row][move.from.col] = null

                val eval = minimax(newBoard, depth - 1, alpha, b, true, aiColor, opponent)
                minEval = min(minEval, eval)
                b = min(b, eval)
                if (b <= alpha) break
            }
            return minEval
        }
    }

    private fun evaluate(board: Board, aiColor: PieceColor): Int {
        var score = 0
        for (row in 0..9) {
            for (col in 0..8) {
                val piece = board.grid[row][col] ?: continue
                val value = pieceValues[piece.type] ?: 0

                // Positional bonus for pawns
                var bonus = 0
                if (piece.type == PieceType.PAWN) {
                    val crossed = if (piece.color == PieceColor.RED) row <= 4 else row >= 5
                    if (crossed) bonus = pawnCrossedBonus
                }

                val totalValue = value + bonus
                if (piece.color == aiColor) {
                    score += totalValue
                } else {
                    score -= totalValue
                }
            }
        }
        return score
    }

    private fun generateAllMoves(board: Board, color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        val tempEngine = ChineseChessEngine()
        tempEngine.setBoard(board.clone(), color)

        for (row in 0..9) {
            for (col in 0..8) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != color) continue
                val from = Position(row, col)
                val targets = tempEngine.getValidMoves(from)
                for (to in targets) {
                    moves.add(Move(from, to, board.pieceAt(to)))
                }
            }
        }

        // Order by capture value
        return moves.sortedByDescending { move ->
            move.captured?.let { pieceValues[it.type] ?: 0 } ?: 0
        }
    }

    private fun isInCheckStatic(board: Board, color: PieceColor): Boolean {
        val kingPos = findKing(board, color) ?: return true
        val opponent = if (color == PieceColor.RED) PieceColor.BLACK else PieceColor.RED
        val tempEngine = ChineseChessEngine()
        tempEngine.setBoard(board.clone(), opponent)

        for (row in 0..9) {
            for (col in 0..8) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != opponent) continue
                val targets = tempEngine.getValidMoves(Position(row, col))
                if (kingPos in targets) return true
            }
        }
        return false
    }

    private fun findKing(board: Board, color: PieceColor): Position? {
        for (row in 0..9) {
            for (col in 0..8) {
                val p = board.grid[row][col]
                if (p != null && p.type == PieceType.KING && p.color == color) {
                    return Position(row, col)
                }
            }
        }
        return null
    }
}
