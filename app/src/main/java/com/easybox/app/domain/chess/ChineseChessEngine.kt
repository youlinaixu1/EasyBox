package com.easybox.app.domain.chess

import com.easybox.app.domain.chess.model.*

class ChineseChessEngine {
    private var board = Board()
    var currentPlayer = PieceColor.RED

    companion object {
        fun createInitialBoard(): Board {
            val board = Board()
            // Black pieces (top, rows 0-4)
            board.grid[0][0] = Piece(PieceType.ROOK, PieceColor.BLACK)
            board.grid[0][1] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
            board.grid[0][2] = Piece(PieceType.ELEPHANT, PieceColor.BLACK)
            board.grid[0][3] = Piece(PieceType.ADVISOR, PieceColor.BLACK)
            board.grid[0][4] = Piece(PieceType.KING, PieceColor.BLACK)
            board.grid[0][5] = Piece(PieceType.ADVISOR, PieceColor.BLACK)
            board.grid[0][6] = Piece(PieceType.ELEPHANT, PieceColor.BLACK)
            board.grid[0][7] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
            board.grid[0][8] = Piece(PieceType.ROOK, PieceColor.BLACK)
            board.grid[2][1] = Piece(PieceType.CANNON, PieceColor.BLACK)
            board.grid[2][7] = Piece(PieceType.CANNON, PieceColor.BLACK)
            board.grid[3][0] = Piece(PieceType.PAWN, PieceColor.BLACK)
            board.grid[3][2] = Piece(PieceType.PAWN, PieceColor.BLACK)
            board.grid[3][4] = Piece(PieceType.PAWN, PieceColor.BLACK)
            board.grid[3][6] = Piece(PieceType.PAWN, PieceColor.BLACK)
            board.grid[3][8] = Piece(PieceType.PAWN, PieceColor.BLACK)

            // Red pieces (bottom, rows 5-9)
            board.grid[9][0] = Piece(PieceType.ROOK, PieceColor.RED)
            board.grid[9][1] = Piece(PieceType.KNIGHT, PieceColor.RED)
            board.grid[9][2] = Piece(PieceType.ELEPHANT, PieceColor.RED)
            board.grid[9][3] = Piece(PieceType.ADVISOR, PieceColor.RED)
            board.grid[9][4] = Piece(PieceType.KING, PieceColor.RED)
            board.grid[9][5] = Piece(PieceType.ADVISOR, PieceColor.RED)
            board.grid[9][6] = Piece(PieceType.ELEPHANT, PieceColor.RED)
            board.grid[9][7] = Piece(PieceType.KNIGHT, PieceColor.RED)
            board.grid[9][8] = Piece(PieceType.ROOK, PieceColor.RED)
            board.grid[7][1] = Piece(PieceType.CANNON, PieceColor.RED)
            board.grid[7][7] = Piece(PieceType.CANNON, PieceColor.RED)
            board.grid[6][0] = Piece(PieceType.PAWN, PieceColor.RED)
            board.grid[6][2] = Piece(PieceType.PAWN, PieceColor.RED)
            board.grid[6][4] = Piece(PieceType.PAWN, PieceColor.RED)
            board.grid[6][6] = Piece(PieceType.PAWN, PieceColor.RED)
            board.grid[6][8] = Piece(PieceType.PAWN, PieceColor.RED)

            return board
        }
    }

    fun reset() {
        board = createInitialBoard()
        currentPlayer = PieceColor.RED
    }

    fun getBoard(): Board = board

    fun setBoard(newBoard: Board, newPlayer: PieceColor) {
        board = newBoard
        currentPlayer = newPlayer
    }

    fun getValidMoves(from: Position): List<Position> {
        val piece = board.pieceAt(from) ?: return emptyList()
        if (piece.color != currentPlayer) return emptyList()

        val candidates = generateRawMoves(from, piece)
        return candidates.filter { to ->
            val cloned = board.clone()
            cloned.grid[to.row][to.col] = cloned.grid[from.row][from.col]
            cloned.grid[from.row][from.col] = null
            !isInCheck(cloned, currentPlayer)
        }
    }

    fun getAllValidMoves(): List<Move> {
        val moves = mutableListOf<Move>()
        for (row in 0..9) {
            for (col in 0..8) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != currentPlayer) continue
                val from = Position(row, col)
                val targets = getValidMoves(from)
                for (to in targets) {
                    moves.add(Move(from, to, board.pieceAt(to)))
                }
            }
        }
        return moves
    }

    fun makeMove(move: Move): Boolean {
        val piece = board.pieceAt(move.from) ?: return false
        if (piece.color != currentPlayer) return false

        val validMoves = getValidMoves(move.from)
        if (move.to !in validMoves) return false

        board.grid[move.to.row][move.to.col] = piece
        board.grid[move.from.row][move.from.col] = null
        currentPlayer = if (currentPlayer == PieceColor.RED) PieceColor.BLACK else PieceColor.RED
        return true
    }

    fun isGameOver(): Boolean {
        val moves = getAllValidMoves()
        return moves.isEmpty()
    }

    fun isInCheck(color: PieceColor): Boolean = isInCheck(board, color)

    private fun isInCheck(board: Board, color: PieceColor): Boolean {
        val kingPos = findKing(board, color) ?: return true
        val opponent = if (color == PieceColor.RED) PieceColor.BLACK else PieceColor.RED
        for (row in 0..9) {
            for (col in 0..8) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != opponent) continue
                val rawMoves = generateRawMoves(Position(row, col), piece, board)
                if (kingPos in rawMoves) return true
            }
        }
        // Flying general check
        return flyingGeneralCheck(board, kingPos, opponent)
    }

    private fun flyingGeneralCheck(board: Board, kingPos: Position, opponent: PieceColor): Boolean {
        val opponentKing = findKing(board, opponent) ?: return false
        if (kingPos.col != opponentKing.col) return false
        val minRow = minOf(kingPos.row, opponentKing.row)
        val maxRow = maxOf(kingPos.row, opponentKing.row)
        for (r in minRow + 1 until maxRow) {
            if (board.grid[r][kingPos.col] != null) return false
        }
        return true
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

    private fun generateRawMoves(from: Position, piece: Piece, b: Board = board): List<Position> {
        return when (piece.type) {
            PieceType.KING -> kingMoves(from, piece.color, b)
            PieceType.ADVISOR -> advisorMoves(from, piece.color, b)
            PieceType.ELEPHANT -> elephantMoves(from, piece.color, b)
            PieceType.KNIGHT -> knightMoves(from, piece.color, b)
            PieceType.ROOK -> rookMoves(from, piece.color, b)
            PieceType.CANNON -> cannonMoves(from, piece.color, b)
            PieceType.PAWN -> pawnMoves(from, piece.color, b)
        }
    }

    private fun inPalace(row: Int, col: Int, color: PieceColor): Boolean {
        return col in 3..5 && if (color == PieceColor.RED) row in 7..9 else row in 0..2
    }

    private fun kingMoves(from: Position, color: PieceColor, b: Board): List<Position> {
        val moves = mutableListOf<Position>()
        val dirs = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        for ((dr, dc) in dirs) {
            val nr = from.row + dr
            val nc = from.col + dc
            if (inPalace(nr, nc, color)) {
                val target = b.grid[nr][nc]
                if (target == null || target.color != color) {
                    moves.add(Position(nr, nc))
                }
            }
        }
        return moves
    }

    private fun advisorMoves(from: Position, color: PieceColor, b: Board): List<Position> {
        val moves = mutableListOf<Position>()
        val dirs = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((dr, dc) in dirs) {
            val nr = from.row + dr
            val nc = from.col + dc
            if (inPalace(nr, nc, color)) {
                val target = b.grid[nr][nc]
                if (target == null || target.color != color) {
                    moves.add(Position(nr, nc))
                }
            }
        }
        return moves
    }

    private fun elephantMoves(from: Position, color: PieceColor, b: Board): List<Position> {
        val moves = mutableListOf<Position>()
        val eyes = listOf(
            Position(from.row - 1, from.col - 1) to Position(from.row - 2, from.col - 2),
            Position(from.row - 1, from.col + 1) to Position(from.row - 2, from.col + 2),
            Position(from.row + 1, from.col - 1) to Position(from.row + 2, from.col - 2),
            Position(from.row + 1, from.col + 1) to Position(from.row + 2, from.col + 2)
        )
        for ((eye, target) in eyes) {
            if (target.row !in 0..9 || target.col !in 0..8) continue
            // Cannot cross river
            if (color == PieceColor.RED && target.row < 5) continue
            if (color == PieceColor.BLACK && target.row > 4) continue
            // Eye blocking
            if (b.grid[eye.row][eye.col] != null) continue
            val t = b.grid[target.row][target.col]
            if (t == null || t.color != color) {
                moves.add(target)
            }
        }
        return moves
    }

    private fun knightMoves(from: Position, color: PieceColor, b: Board): List<Position> {
        val moves = mutableListOf<Position>()
        val steps = listOf(
            // leg position, target position
            Position(from.row - 1, from.col) to Position(from.row - 2, from.col - 1),
            Position(from.row - 1, from.col) to Position(from.row - 2, from.col + 1),
            Position(from.row + 1, from.col) to Position(from.row + 2, from.col - 1),
            Position(from.row + 1, from.col) to Position(from.row + 2, from.col + 1),
            Position(from.row, from.col - 1) to Position(from.row - 1, from.col - 2),
            Position(from.row, from.col - 1) to Position(from.row + 1, from.col - 2),
            Position(from.row, from.col + 1) to Position(from.row - 1, from.col + 2),
            Position(from.row, from.col + 1) to Position(from.row + 1, from.col + 2)
        )
        for ((leg, target) in steps) {
            if (target.row !in 0..9 || target.col !in 0..8) continue
            // Leg blocking
            if (b.grid[leg.row][leg.col] != null) continue
            val t = b.grid[target.row][target.col]
            if (t == null || t.color != color) {
                moves.add(target)
            }
        }
        return moves
    }

    private fun rookMoves(from: Position, color: PieceColor, b: Board): List<Position> {
        val moves = mutableListOf<Position>()
        val dirs = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        for ((dr, dc) in dirs) {
            var r = from.row + dr
            var c = from.col + dc
            while (r in 0..9 && c in 0..8) {
                val target = b.grid[r][c]
                if (target == null) {
                    moves.add(Position(r, c))
                } else {
                    if (target.color != color) moves.add(Position(r, c))
                    break
                }
                r += dr
                c += dc
            }
        }
        return moves
    }

    private fun cannonMoves(from: Position, color: PieceColor, b: Board): List<Position> {
        val moves = mutableListOf<Position>()
        val dirs = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        for ((dr, dc) in dirs) {
            var r = from.row + dr
            var c = from.col + dc
            // Movement without capture (like rook)
            while (r in 0..9 && c in 0..8) {
                if (b.grid[r][c] != null) break
                moves.add(Position(r, c))
                r += dr
                c += dc
            }
            // Skip the platform piece
            r += dr
            c += dc
            // Find target behind platform
            while (r in 0..9 && c in 0..8) {
                val target = b.grid[r][c]
                if (target != null) {
                    if (target.color != color) moves.add(Position(r, c))
                    break
                }
                r += dr
                c += dc
            }
        }
        return moves
    }

    private fun pawnMoves(from: Position, color: PieceColor, b: Board): List<Position> {
        val moves = mutableListOf<Position>()
        val forward = if (color == PieceColor.RED) -1 else 1
        val crossedRiver = if (color == PieceColor.RED) from.row <= 4 else from.row >= 5

        // Forward
        val fr = from.row + forward
        if (fr in 0..9) {
            val target = b.grid[fr][from.col]
            if (target == null || target.color != color) {
                moves.add(Position(fr, from.col))
            }
        }

        // Sideways (only after crossing river)
        if (crossedRiver) {
            for (dc in listOf(-1, 1)) {
                val nc = from.col + dc
                if (nc in 0..8) {
                    val target = b.grid[from.row][nc]
                    if (target == null || target.color != color) {
                        moves.add(Position(from.row, nc))
                    }
                }
            }
        }

        return moves
    }
}
