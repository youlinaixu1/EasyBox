package com.easybox.app.domain.doudizhu

// Card: 0-51 = standard cards (rank 0-12, suit 0-3), 52 = small joker, 53 = big joker
// Rank order: 3(0) < 4(1) < 5(2) < 6(3) < 7(4) < 8(5) < 9(6) < 10(7) < J(8) < Q(9) < K(10) < A(11) < 2(12) < SmallJoker(13) < BigJoker(14)

data class Card(val id: Int) : Comparable<Card> {
    val rank: Int get() = when {
        id == 52 -> 13  // small joker
        id == 53 -> 14  // big joker
        else -> id / 4  // 0=3, 1=4, ..., 12=2
    }
    val suit: Int get() = when {
        id >= 52 -> -1
        else -> id % 4  // 0=spade, 1=heart, 2=club, 3=diamond
    }
    val display: String get() = when (id) {
        52 -> "小王"
        53 -> "大王"
        else -> {
            val rankChar = when (rank) {
                0 -> "3"; 1 -> "4"; 2 -> "5"; 3 -> "6"; 4 -> "7"; 5 -> "8"; 6 -> "9"
                7 -> "10"; 8 -> "J"; 9 -> "Q"; 10 -> "K"; 11 -> "A"; 12 -> "2"
                else -> "?"
            }
            val suitChar = when (suit) {
                0 -> "♠"; 1 -> "♥"; 2 -> "♣"; 3 -> "♦"; else -> ""
            }
            "$suitChar$rankChar"
        }
    }
    override fun compareTo(other: Card): Int = this.rank.compareTo(other.rank)
}

enum class PlayType {
    SINGLE, PAIR, TRIPLE, TRIPLE_ONE, TRIPLE_TWO,
    STRAIGHT, DOUBLE_STRAIGHT, TRIPLE_STRAIGHT,
    BOMB, ROCKET, FOUR_TWO,
    PASS
}

data class Play(
    val cards: List<Card>,
    val type: PlayType,
    val rank: Int  // primary rank for comparison
)

enum class DPlayer { PLAYER_1, PLAYER_2, PLAYER_3 }
enum class DPhase { BIDDING, PLAYING, FINISHED }
enum class DBid { NONE, THREE }

data class DGameState(
    val hands: Map<DPlayer, List<Card>>,
    val bottomCards: List<Card>,
    val currentPlayer: DPlayer,
    val landlord: DPlayer?,
    val lastPlay: Play?,
    val lastPlayer: DPlayer?,
    val passCount: Int,
    val phase: DPhase,
    val bidTurn: DPlayer?,
    val bidResponses: Int = 0,  // count of players who have responded in bidding
    val bidWinner: DPlayer? = null,  // player who bid THREE
    val winner: DPlayer?
)

fun createDeck(): List<Card> = (0..53).map { Card(it) }.shuffled()

fun dealCards(): Triple<List<Card>, List<Card>, Triple<List<Card>, List<Card>, List<Card>>> {
    val deck = createDeck()
    val bottom = deck.take(3)
    val remaining = deck.drop(3)
    val p1 = remaining.filterIndexed { i, _ -> i % 3 == 0 }
    val p2 = remaining.filterIndexed { i, _ -> i % 3 == 1 }
    val p3 = remaining.filterIndexed { i, _ -> i % 3 == 2 }
    return Triple(bottom, p1, Triple(p1, p2, p3))
}

fun detectPlay(cards: List<Card>): Play? {
    if (cards.isEmpty()) return null
    val sorted = cards.sortedBy { it.rank }
    val n = sorted.size
    val ranks = sorted.map { it.rank }

    // Rocket
    if (n == 2 && ranks[0] == 13 && ranks[1] == 14)
        return Play(cards, PlayType.ROCKET, 14)

    // Bomb: 4 of same rank (ranks 0-12, not jokers)
    if (n == 4 && ranks.all { it == ranks[0] } && ranks[0] <= 12)
        return Play(cards, PlayType.BOMB, ranks[0])

    // Group cards by rank
    val groups = sorted.groupBy { it.rank }.toList().sortedBy { (rank, _) -> rank }

    // Single
    if (n == 1) return Play(cards, PlayType.SINGLE, ranks[0])

    // Pair
    if (n == 2 && groups.size == 1) return Play(cards, PlayType.PAIR, ranks[0])

    // Triple
    if (n == 3 && groups.size == 1) return Play(cards, PlayType.TRIPLE, ranks[0])

    // Triple + 1
    if (n == 4) {
        val triple = groups.firstOrNull { (_, g) -> g.size == 3 }
        val single = groups.firstOrNull { (_, g) -> g.size == 1 }
        if (triple != null && single != null)
            return Play(cards, PlayType.TRIPLE_ONE, triple.first)
    }

    // Triple + 2
    if (n == 5) {
        val triple = groups.firstOrNull { (_, g) -> g.size == 3 }
        val pair = groups.firstOrNull { (_, g) -> g.size == 2 }
        if (triple != null && pair != null)
            return Play(cards, PlayType.TRIPLE_TWO, triple.first)
    }

    // Four + 2 singles
    if (n == 6) {
        val four = groups.firstOrNull { (_, g) -> g.size == 4 }
        if (four != null) {
            val singles = groups.filter { (_, g) -> g.size == 1 || g.size == 2 }
            val totalOther = singles.sumOf { (_, g) -> g.size }
            if (totalOther == 2) return Play(cards, PlayType.FOUR_TWO, four.first)
        }
    }

    // Straight: 5+ consecutive singles, no 2 or jokers
    if (n >= 5 && groups.all { (_, g) -> g.size == 1 }) {
        val isConsecutive = ranks.zipWithNext().all { (a, b) -> b == a + 1 }
        val maxRank = ranks.last()
        if (isConsecutive && maxRank < 13) // can't include 2 or jokers in straight
            return Play(cards, PlayType.STRAIGHT, maxRank)
    }

    // Double Straight: 3+ consecutive pairs
    if (n >= 6 && n % 2 == 0 && groups.all { (_, g) -> g.size == 2 }) {
        val pairRanks = groups.map { (r, _) -> r }
        val isConsecutive = pairRanks.zipWithNext().all { (a, b) -> b == a + 1 }
        if (isConsecutive && pairRanks.last() < 13)
            return Play(cards, PlayType.DOUBLE_STRAIGHT, pairRanks.last())
    }

    // Triple Straight (airplane): 2+ consecutive triples
    val triples = groups.filter { (_, g) -> g.size >= 3 }
    if (triples.size >= 2) {
        val tripleRanks = triples.map { (r, _) -> r }
        val isConsecutive = tripleRanks.zipWithNext().all { (a, b) -> b == a + 1 }
        if (isConsecutive && tripleRanks.last() < 13) {
            val tripleCount = triples.size
            val otherCards = n - tripleCount * 3
            if (otherCards == 0)
                return Play(cards, PlayType.TRIPLE_STRAIGHT, tripleRanks.last())
            if (otherCards == tripleCount) // each triple brings a single
                return Play(cards, PlayType.TRIPLE_STRAIGHT, tripleRanks.last())
            if (otherCards == tripleCount * 2) // each triple brings a pair
                return Play(cards, PlayType.TRIPLE_STRAIGHT, tripleRanks.last())
        }
    }

    return null
}

fun canBeat(newPlay: Play, lastPlay: Play): Boolean {
    // Rocket beats everything
    if (newPlay.type == PlayType.ROCKET) return true
    // Bomb beats non-bomb, non-rocket
    if (newPlay.type == PlayType.BOMB && lastPlay.type != PlayType.BOMB && lastPlay.type != PlayType.ROCKET)
        return true
    // Same type, higher rank
    if (newPlay.type == lastPlay.type && newPlay.cards.size == lastPlay.cards.size)
        return newPlay.rank > lastPlay.rank
    return false
}

fun isPlayValid(play: Play, lastPlay: Play?, isNewRound: Boolean): Boolean {
    if (isNewRound || lastPlay == null) return true
    return canBeat(play, lastPlay)
}
