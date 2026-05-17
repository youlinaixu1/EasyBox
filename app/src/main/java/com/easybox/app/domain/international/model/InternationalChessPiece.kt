package com.easybox.app.domain.international.model

enum class IColor { WHITE, BLACK }

enum class IType(val symbol: String) {
    KING("K"),
    QUEEN("Q"),
    ROOK("R"),
    BISHOP("B"),
    KNIGHT("N"),
    PAWN("P")
}

data class IPiece(
    val type: IType,
    val color: IColor
) {
    val symbol: String
        get() {
            val s = when (type) {
                IType.KING -> "♔" to "♚"
                IType.QUEEN -> "♕" to "♛"
                IType.ROOK -> "♖" to "♜"
                IType.BISHOP -> "♗" to "♝"
                IType.KNIGHT -> "♘" to "♞"
                IType.PAWN -> "♙" to "♟"
            }
            return if (color == IColor.WHITE) s.first else s.second
        }
}

data class IPosition(val row: Int, val col: Int)

data class IMove(
    val from: IPosition,
    val to: IPosition,
    val captured: IPiece? = null,
    val promotion: IType? = null
)

data class IBoard(
    val grid: Array<Array<IPiece?>> = Array(8) { Array(8) { null } }
) {
    fun pieceAt(pos: IPosition): IPiece? = grid[pos.row][pos.col]
    fun pieceAt(row: Int, col: Int): IPiece? = grid[row][col]

    fun clone(): IBoard {
        val newGrid = Array(8) { row -> Array(8) { col -> grid[row][col] } }
        return IBoard(newGrid)
    }
}
