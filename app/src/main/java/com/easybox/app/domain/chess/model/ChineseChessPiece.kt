package com.easybox.app.domain.chess.model

enum class PieceColor { RED, BLACK }

enum class PieceType(val symbol: String) {
    KING("帅"),
    ADVISOR("仕"),
    ELEPHANT("相"),
    KNIGHT("馬"),
    ROOK("車"),
    CANNON("炮"),
    PAWN("兵")
}

data class Piece(
    val type: PieceType,
    val color: PieceColor
) {
    val symbol: String
        get() = if (color == PieceColor.RED) {
            when (type) {
                PieceType.KING -> "帅"
                PieceType.ADVISOR -> "仕"
                PieceType.ELEPHANT -> "相"
                PieceType.KNIGHT -> "馬"
                PieceType.ROOK -> "車"
                PieceType.CANNON -> "炮"
                PieceType.PAWN -> "兵"
            }
        } else {
            when (type) {
                PieceType.KING -> "将"
                PieceType.ADVISOR -> "士"
                PieceType.ELEPHANT -> "象"
                PieceType.KNIGHT -> "马"
                PieceType.ROOK -> "车"
                PieceType.CANNON -> "砲"
                PieceType.PAWN -> "卒"
            }
        }
}

data class Position(val row: Int, val col: Int)

data class Move(val from: Position, val to: Position, val captured: Piece? = null)

data class Board(
    val grid: Array<Array<Piece?>> = Array(10) { Array(9) { null } }
) {
    fun pieceAt(pos: Position): Piece? = grid[pos.row][pos.col]
    fun pieceAt(row: Int, col: Int): Piece? = grid[row][col]

    fun clone(): Board {
        val newGrid = Array(10) { row -> Array(9) { col -> grid[row][col] } }
        return Board(newGrid)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        for (r in 0..9) for (c in 0..8) {
            if (grid[r][c] != other.grid[r][c]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 0
        for (r in 0..9) for (c in 0..8) {
            result = 31 * result + (grid[r][c]?.hashCode() ?: 0)
        }
        return result
    }
}
