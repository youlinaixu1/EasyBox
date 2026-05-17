package com.easybox.app.domain.international

import com.easybox.app.domain.international.model.*

class InternationalChessEngine {
    private var board = IBoard()
    var currentPlayer = IColor.WHITE
    private var canCastleWhiteKingSide = true
    private var canCastleWhiteQueenSide = true
    private var canCastleBlackKingSide = true
    private var canCastleBlackQueenSide = true
    private var enPassantTarget: IPosition? = null
    private var halfMoveClock = 0
    var fullMoveNumber = 1

    companion object {
        fun createInitialBoard(): IBoard {
            val board = IBoard()
            // Black pieces (row 0)
            board.grid[0][0] = IPiece(IType.ROOK, IColor.BLACK)
            board.grid[0][1] = IPiece(IType.KNIGHT, IColor.BLACK)
            board.grid[0][2] = IPiece(IType.BISHOP, IColor.BLACK)
            board.grid[0][3] = IPiece(IType.QUEEN, IColor.BLACK)
            board.grid[0][4] = IPiece(IType.KING, IColor.BLACK)
            board.grid[0][5] = IPiece(IType.BISHOP, IColor.BLACK)
            board.grid[0][6] = IPiece(IType.KNIGHT, IColor.BLACK)
            board.grid[0][7] = IPiece(IType.ROOK, IColor.BLACK)
            for (col in 0..7) board.grid[1][col] = IPiece(IType.PAWN, IColor.BLACK)

            // White pieces (row 7)
            board.grid[7][0] = IPiece(IType.ROOK, IColor.WHITE)
            board.grid[7][1] = IPiece(IType.KNIGHT, IColor.WHITE)
            board.grid[7][2] = IPiece(IType.BISHOP, IColor.WHITE)
            board.grid[7][3] = IPiece(IType.QUEEN, IColor.WHITE)
            board.grid[7][4] = IPiece(IType.KING, IColor.WHITE)
            board.grid[7][5] = IPiece(IType.BISHOP, IColor.WHITE)
            board.grid[7][6] = IPiece(IType.KNIGHT, IColor.WHITE)
            board.grid[7][7] = IPiece(IType.ROOK, IColor.WHITE)
            for (col in 0..7) board.grid[6][col] = IPiece(IType.PAWN, IColor.WHITE)

            return board
        }
    }

    fun reset() {
        board = createInitialBoard()
        currentPlayer = IColor.WHITE
        canCastleWhiteKingSide = true
        canCastleWhiteQueenSide = true
        canCastleBlackKingSide = true
        canCastleBlackQueenSide = true
        enPassantTarget = null
        halfMoveClock = 0
        fullMoveNumber = 1
    }

    fun getBoard(): IBoard = board

    fun setBoard(newBoard: IBoard, newPlayer: IColor) {
        board = newBoard
        currentPlayer = newPlayer
    }

    fun getValidMoves(from: IPosition): List<IPosition> {
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

    fun getAllValidMoves(): List<IMove> {
        val moves = mutableListOf<IMove>()
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != currentPlayer) continue
                val from = IPosition(row, col)
                val targets = getValidMoves(from)
                for (to in targets) {
                    val promotion = if (piece.type == IType.PAWN && (to.row == 0 || to.row == 7))
                        IType.QUEEN else null
                    moves.add(IMove(from, to, board.pieceAt(to), promotion))
                }
            }
        }
        return moves
    }

    fun makeMove(move: IMove): Boolean {
        val piece = board.pieceAt(move.from) ?: return false
        if (piece.color != currentPlayer) return false

        val validMoves = getValidMoves(move.from)
        if (move.to !in validMoves) return false

        // Handle castling
        if (piece.type == IType.KING && kotlin.math.abs(move.to.col - move.from.col) == 2) {
            val rookFromCol = if (move.to.col > move.from.col) 7 else 0
            val rookToCol = if (move.to.col > move.from.col) move.to.col - 1 else move.to.col + 1
            board.grid[move.from.row][rookToCol] = board.grid[move.from.row][rookFromCol]
            board.grid[move.from.row][rookFromCol] = null
        }

        // Handle en passant capture
        if (piece.type == IType.PAWN && move.to == enPassantTarget) {
            val capturedRow = if (piece.color == IColor.WHITE) move.to.row + 1 else move.to.row - 1
            board.grid[capturedRow][move.to.col] = null
        }

        // Update en passant target
        enPassantTarget = if (piece.type == IType.PAWN && kotlin.math.abs(move.to.row - move.from.row) == 2) {
            val midRow = (move.from.row + move.to.row) / 2
            IPosition(midRow, move.from.col)
        } else null

        // Handle promotion
        if (move.promotion != null) {
            board.grid[move.to.row][move.to.col] = IPiece(move.promotion, piece.color)
        } else {
            board.grid[move.to.row][move.to.col] = piece
        }
        board.grid[move.from.row][move.from.col] = null

        // Update castling rights
        updateCastlingRights(move.from, piece)

        // Switch player
        currentPlayer = if (currentPlayer == IColor.WHITE) IColor.BLACK else IColor.WHITE
        if (currentPlayer == IColor.WHITE) fullMoveNumber++

        return true
    }

    private fun updateCastlingRights(from: IPosition, piece: IPiece) {
        if (piece.type == IType.KING) {
            if (piece.color == IColor.WHITE) {
                canCastleWhiteKingSide = false
                canCastleWhiteQueenSide = false
            } else {
                canCastleBlackKingSide = false
                canCastleBlackQueenSide = false
            }
        }
        if (piece.type == IType.ROOK) {
            if (piece.color == IColor.WHITE) {
                if (from == IPosition(7, 0)) canCastleWhiteQueenSide = false
                if (from == IPosition(7, 7)) canCastleWhiteKingSide = false
            } else {
                if (from == IPosition(0, 0)) canCastleBlackQueenSide = false
                if (from == IPosition(0, 7)) canCastleBlackKingSide = false
            }
        }
    }

    fun isGameOver(): Boolean = getAllValidMoves().isEmpty()

    fun isInCheck(color: IColor): Boolean = isInCheck(board, color)

    private fun isInCheck(board: IBoard, color: IColor): Boolean {
        val kingPos = findKing(board, color) ?: return true
        val opponent = if (color == IColor.WHITE) IColor.BLACK else IColor.WHITE
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board.grid[row][col] ?: continue
                if (piece.color != opponent) continue
                val rawMoves = generateRawMoves(IPosition(row, col), piece, board)
                if (kingPos in rawMoves) return true
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

    private fun generateRawMoves(from: IPosition, piece: IPiece, b: IBoard = board): List<IPosition> {
        val moves = when (piece.type) {
            IType.KING -> kingMoves(from, piece.color, b)
            IType.QUEEN -> queenMoves(from, piece.color, b)
            IType.ROOK -> rookMoves(from, piece.color, b)
            IType.BISHOP -> bishopMoves(from, piece.color, b)
            IType.KNIGHT -> knightMoves(from, piece.color, b)
            IType.PAWN -> pawnMoves(from, piece.color, b)
        }
        return moves
    }

    private fun canMoveTo(pos: IPosition, color: IColor, b: IBoard): Boolean {
        if (pos.row !in 0..7 || pos.col !in 0..7) return false
        val target = b.grid[pos.row][pos.col]
        return target == null || target.color != color
    }

    private fun kingMoves(from: IPosition, color: IColor, b: IBoard): List<IPosition> {
        val moves = mutableListOf<IPosition>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val pos = IPosition(from.row + dr, from.col + dc)
            if (canMoveTo(pos, color, b)) moves.add(pos)
        }
        // Castling
        val row = if (color == IColor.WHITE) 7 else 0
        if (from == IPosition(row, 4)) {
            val canKing = if (color == IColor.WHITE) canCastleWhiteKingSide else canCastleBlackKingSide
            val canQueen = if (color == IColor.WHITE) canCastleWhiteQueenSide else canCastleBlackQueenSide

            if (canKing &&
                b.grid[row][5] == null && b.grid[row][6] == null &&
                !isSquareAttacked(IPosition(row, 5), color, b) &&
                !isSquareAttacked(IPosition(row, 6), color, b)
            ) {
                moves.add(IPosition(row, 6))
            }
            if (canQueen &&
                b.grid[row][3] == null && b.grid[row][2] == null && b.grid[row][1] == null &&
                !isSquareAttacked(IPosition(row, 3), color, b) &&
                !isSquareAttacked(IPosition(row, 2), color, b)
            ) {
                moves.add(IPosition(row, 2))
            }
        }
        return moves
    }

    private fun queenMoves(from: IPosition, color: IColor, b: IBoard): List<IPosition> {
        return rookMoves(from, color, b) + bishopMoves(from, color, b)
    }

    private fun rookMoves(from: IPosition, color: IColor, b: IBoard): List<IPosition> {
        val moves = mutableListOf<IPosition>()
        val dirs = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        for ((dr, dc) in dirs) {
            var r = from.row + dr
            var c = from.col + dc
            while (canMoveTo(IPosition(r, c), color, b)) {
                moves.add(IPosition(r, c))
                if (b.grid[r][c] != null) break
                r += dr
                c += dc
            }
        }
        return moves
    }

    private fun bishopMoves(from: IPosition, color: IColor, b: IBoard): List<IPosition> {
        val moves = mutableListOf<IPosition>()
        val dirs = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((dr, dc) in dirs) {
            var r = from.row + dr
            var c = from.col + dc
            while (canMoveTo(IPosition(r, c), color, b)) {
                moves.add(IPosition(r, c))
                if (b.grid[r][c] != null) break
                r += dr
                c += dc
            }
        }
        return moves
    }

    private fun knightMoves(from: IPosition, color: IColor, b: IBoard): List<IPosition> {
        val moves = mutableListOf<IPosition>()
        val offsets = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((dr, dc) in offsets) {
            val pos = IPosition(from.row + dr, from.col + dc)
            if (canMoveTo(pos, color, b)) moves.add(pos)
        }
        return moves
    }

    private fun pawnMoves(from: IPosition, color: IColor, b: IBoard): List<IPosition> {
        val moves = mutableListOf<IPosition>()
        val direction = if (color == IColor.WHITE) -1 else 1
        val startRow = if (color == IColor.WHITE) 6 else 1

        // Forward one
        val oneForward = IPosition(from.row + direction, from.col)
        if (b.grid[oneForward.row][oneForward.col] == null) {
            moves.add(oneForward)
            // Forward two from starting position
            val twoForward = IPosition(from.row + 2 * direction, from.col)
            if (from.row == startRow && b.grid[twoForward.row][twoForward.col] == null) {
                moves.add(twoForward)
            }
        }

        // Captures
        for (dc in listOf(-1, 1)) {
            val capturePos = IPosition(from.row + direction, from.col + dc)
            if (capturePos.col in 0..7) {
                val target = b.grid[capturePos.row][capturePos.col]
                if (target != null && target.color != color) {
                    moves.add(capturePos)
                }
                // En passant
                if (capturePos == enPassantTarget) {
                    moves.add(capturePos)
                }
            }
        }

        return moves
    }

    private fun isSquareAttacked(pos: IPosition, color: IColor, b: IBoard): Boolean {
        val opponent = if (color == IColor.WHITE) IColor.BLACK else IColor.WHITE
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = b.grid[row][col] ?: continue
                if (piece.color != opponent) continue
                val targets = when (piece.type) {
                    IType.KING -> {
                        val m = mutableListOf<IPosition>()
                        for (dr in -1..1) for (dc in -1..1) {
                            if (dr == 0 && dc == 0) continue
                            val p = IPosition(row + dr, col + dc)
                            if (p.row in 0..7 && p.col in 0..7) m.add(p)
                        }
                        m
                    }
                    IType.PAWN -> {
                        val dir = if (opponent == IColor.WHITE) -1 else 1
                        listOf(-1, 1).mapNotNull { dc ->
                            val p = IPosition(row + dir, col + dc)
                            if (p.col in 0..7) p else null
                        }
                    }
                    else -> generateRawMoves(IPosition(row, col), piece, b)
                }
                if (pos in targets) return true
            }
        }
        return false
    }
}
