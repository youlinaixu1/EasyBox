package com.easybox.app.domain.doudizhu

class DouDiZhuAI {

    fun shouldBid(hand: List<Card>): DBid {
        val hasRocket = hand.any { it.id == 52 } && hand.any { it.id == 53 }
        val bombCount = hand.groupBy { it.rank }.count { (rank, cards) -> rank < 13 && cards.size >= 4 }
        val bigCards = hand.count { it.rank >= 12 }
        val power = (if (hasRocket) 3 else 0) + bombCount * 2 + bigCards
        return if (power >= 3) DBid.THREE else DBid.NONE
    }

    fun pickPlay(hand: List<Card>, lastPlay: Play?, isNewRound: Boolean, engine: DouDiZhuEngine): Play? {
        val allPlays = engine.getAllValidPlays(hand, lastPlay, isNewRound)
        if (allPlays.isEmpty()) return if (isNewRound) null else Play(emptyList(), PlayType.PASS, 0)

        val nonPass = allPlays.filter { it.type != PlayType.PASS }

        // If we can clear our hand in one play, do it
        val winningPlay = nonPass.firstOrNull { it.cards.size == hand.size }
        if (winningPlay != null) return winningPlay

        if (isNewRound) {
            return pickLead(nonPass, hand)
        }

        // Must beat current play
        if (nonPass.isEmpty()) return Play(emptyList(), PlayType.PASS, 0)

        return pickFollow(nonPass, hand, lastPlay!!)
    }

    // Lead a new round — prefer to play more cards and useful combos
    private fun pickLead(plays: List<Play>, hand: List<Card>): Play {
        // Score each play: higher score = better lead
        val scored = plays.map { play ->
            var score = 0
            // Favor playing more cards (clear hand faster)
            score += play.cards.size * 10
            // Favor non-single plays
            score += when (play.type) {
                PlayType.SINGLE -> 0
                PlayType.PAIR -> 20
                PlayType.TRIPLE -> 40
                PlayType.TRIPLE_ONE -> 35
                PlayType.TRIPLE_TWO -> 45
                PlayType.STRAIGHT -> 60
                PlayType.DOUBLE_STRAIGHT -> 80
                PlayType.TRIPLE_STRAIGHT -> 90
                PlayType.FOUR_TWO -> 50
                PlayType.BOMB -> -10  // save bombs
                PlayType.ROCKET -> -20 // save rockets
                else -> 0
            }
            // Favor lower rank (save big cards for later)
            score -= play.rank * 2
            // Slight penalty for plays that consume 4-of-a-kind that could be bombs
            val handGroups = hand.groupBy { it.rank }
            play.cards.groupBy { it.rank }.forEach { (rank, cards) ->
                val inHand = handGroups[rank]?.size ?: 0
                if (inHand == 4 && cards.size < 4 && rank < 13) score -= 30 // breaking a bomb
            }
            play to score
        }
        return scored.maxByOrNull { it.second }?.first ?: plays.first()
    }

    // Follow — beat current play with smallest valid play
    private fun pickFollow(plays: List<Play>, hand: List<Card>, lastPlay: Play): Play {
        // Separate bombs/rockets from normal plays
        val normalBeats = plays.filter {
            it.type != PlayType.BOMB && it.type != PlayType.ROCKET
        }
        val bombBeats = plays.filter {
            it.type == PlayType.BOMB || it.type == PlayType.ROCKET
        }

        if (normalBeats.isNotEmpty()) {
            // Score normal plays: prefer smallest rank that matches the type, use more cards
            val scored = normalBeats.map { play ->
                var score = 0
                // Prefer same type as lastPlay
                if (play.type == lastPlay.type) score += 50
                // Prefer using more cards (to clear hand)
                score += play.cards.size * 5
                // Prefer lower rank
                score -= play.rank * 3
                // Avoid breaking bombs
                val handGroups = hand.groupBy { it.rank }
                play.cards.groupBy { it.rank }.forEach { (rank, cards) ->
                    val inHand = handGroups[rank]?.size ?: 0
                    if (inHand == 4 && cards.size < 4 && rank < 13) score -= 50
                }
                play to score
            }
            return scored.maxByOrNull { it.second }?.first ?: normalBeats.first()
        }

        // Only bombs available — use smallest bomb if hand is small, otherwise pass
        if (hand.size <= 8 && bombBeats.isNotEmpty()) {
            return bombBeats.minByOrNull { it.rank }!!
        }

        return Play(emptyList(), PlayType.PASS, 0)
    }
}
