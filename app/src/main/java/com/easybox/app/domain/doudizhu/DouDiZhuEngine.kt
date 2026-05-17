package com.easybox.app.domain.doudizhu

class DouDiZhuEngine {

    fun initGame(): DGameState {
        val (bottom, _, triple) = dealCards()
        return DGameState(
            hands = mapOf(
                DPlayer.PLAYER_1 to triple.first.sortedBy { it.rank },
                DPlayer.PLAYER_2 to triple.second.sortedBy { it.rank },
                DPlayer.PLAYER_3 to triple.third.sortedBy { it.rank }
            ),
            bottomCards = bottom,
            currentPlayer = DPlayer.PLAYER_1,
            landlord = null,
            lastPlay = null,
            lastPlayer = null,
            passCount = 0,
            phase = DPhase.BIDDING,
            bidTurn = DPlayer.PLAYER_1,
            winner = null
        )
    }

    fun placeBid(state: DGameState, player: DPlayer, bid: DBid): DGameState {
        if (state.phase != DPhase.BIDDING || state.bidTurn != player) return state

        val bidOrder = listOf(DPlayer.PLAYER_1, DPlayer.PLAYER_2, DPlayer.PLAYER_3)
        val newResponses = state.bidResponses + 1

        if (bid == DBid.THREE) {
            // This player becomes landlord immediately
            val newHands = state.hands.toMutableMap()
            newHands[player] = (newHands[player]!! + state.bottomCards).sortedBy { it.rank }
            return state.copy(
                hands = newHands,
                landlord = player,
                currentPlayer = player,
                phase = DPhase.PLAYING,
                bidTurn = null,
                bidWinner = player,
                bidResponses = newResponses,
                lastPlay = null, lastPlayer = null, passCount = 0
            )
        }

        // All 3 responded and nobody bid -> redeal
        if (newResponses >= 3) return initGame()

        val nextBidder = bidOrder[(bidOrder.indexOf(player) + 1) % 3]
        return state.copy(bidTurn = nextBidder, bidResponses = newResponses)
    }

    fun playCards(state: DGameState, player: DPlayer, play: Play?): DGameState {
        if (state.phase != DPhase.PLAYING || state.currentPlayer != player) return state

        // Treat null and PASS type both as pass
        val isPass = play == null || play.type == PlayType.PASS
        if (isPass) {
            if (state.lastPlay == null) return state // Can't pass on new round
            val newPassCount = state.passCount + 1
            val nextPlayer = nextPlayer(player)
            if (newPassCount >= 2) {
                return state.copy(
                    currentPlayer = state.lastPlayer!!,
                    lastPlay = null, lastPlayer = null, passCount = 0
                )
            }
            return state.copy(currentPlayer = nextPlayer, passCount = newPassCount, lastPlay = play)
        }

        // play is non-null at this point (null/Pass already handled above)
        val p = play!!
        val isNewRound = state.lastPlay == null || state.lastPlayer == player
        if (!isPlayValid(p, if (isNewRound) null else state.lastPlay, isNewRound)) return state

        // Remove cards from hand
        val newHands = state.hands.toMutableMap()
        val hand = newHands[player]!!.toMutableList()
        for (card in p.cards) {
            val idx = hand.indexOfFirst { it.id == card.id }
            if (idx < 0) return state // Card not in hand
            hand.removeAt(idx)
        }
        newHands[player] = hand

        val won = hand.isEmpty()

        return state.copy(
            hands = newHands,
            currentPlayer = if (won) player else nextPlayer(player),
            lastPlay = p,
            lastPlayer = player,
            passCount = 0,
            phase = if (won) DPhase.FINISHED else DPhase.PLAYING,
            winner = if (won) determineWinner(state, player) else null
        )
    }

    private fun determineWinner(state: DGameState, wonPlayer: DPlayer): DPlayer {
        val landlord = state.landlord ?: return wonPlayer
        return if (wonPlayer == landlord) landlord else {
            // Peasants win, returns the winning peasant
            wonPlayer
        }
    }

    fun nextPlayer(current: DPlayer): DPlayer = when (current) {
        DPlayer.PLAYER_1 -> DPlayer.PLAYER_2
        DPlayer.PLAYER_2 -> DPlayer.PLAYER_3
        DPlayer.PLAYER_3 -> DPlayer.PLAYER_1
    }

    fun getAllValidPlays(hand: List<Card>, lastPlay: Play?, isNewRound: Boolean): List<Play> {
        if (hand.isEmpty()) return listOf(Play(emptyList(), PlayType.PASS, 0))

        val allCombos = generateAllPlays(hand)
        val valid = allCombos.filter { isPlayValid(it, lastPlay, isNewRound) }

        return if (!isNewRound && lastPlay != null) {
            valid + Play(emptyList(), PlayType.PASS, 0) // Allow pass
        } else {
            valid
        }
    }

    private fun generateAllPlays(hand: List<Card>): List<Play> {
        val plays = mutableListOf<Play>()
        val sorted = hand.sortedBy { it.rank }
        val rankGroups = sorted.groupBy { it.rank }

        // Singles
        for (card in sorted) {
            detectPlay(listOf(card))?.let { plays.add(it) }
        }

        // Pairs
        for ((rank, cards) in rankGroups) {
            if (cards.size >= 2) {
                detectPlay(cards.take(2))?.let { plays.add(it) }
            }
        }

        // Triples and their extensions
        for ((rank, cards) in rankGroups) {
            if (cards.size >= 3) {
                val trip = cards.take(3)
                detectPlay(trip)?.let { plays.add(it) }
                // Triple + 1
                val remaining = sorted.filter { it.rank != rank }
                for (r in remaining) {
                    detectPlay(trip + r)?.let { plays.add(it) }
                }
                // Triple + 2
                for ((r2, c2) in rankGroups) {
                    if (r2 != rank && c2.size >= 2) {
                        detectPlay(trip + c2.take(2))?.let { plays.add(it) }
                    }
                }
            }
        }

        // Bombs
        for ((rank, cards) in rankGroups) {
            if (cards.size == 4) {
                detectPlay(cards)?.let { plays.add(it) }
            }
        }

        // Rocket
        val hasSmallJoker = sorted.any { it.id == 52 }
        val hasBigJoker = sorted.any { it.id == 53 }
        if (hasSmallJoker && hasBigJoker) {
            detectPlay(listOf(Card(52), Card(53)))?.let { plays.add(it) }
        }

        // Straights (5+)
        for (len in 5..12) {
            for (startRank in 0..(12 - len)) {
                val straightCards = mutableListOf<Card>()
                var ok = true
                for (r in startRank until startRank + len) {
                    val cardsOfRank = rankGroups[r]
                    if (cardsOfRank == null || cardsOfRank.isEmpty()) { ok = false; break }
                    straightCards.add(cardsOfRank.first())
                }
                if (ok && straightCards.isNotEmpty())
                    detectPlay(straightCards)?.let { plays.add(it) }
            }
        }

        // Double straights (3+)
        for (len in 3..10) {
            for (startRank in 0..(12 - len)) {
                val dblCards = mutableListOf<Card>()
                var ok = true
                for (r in startRank until startRank + len) {
                    val cardsOfRank = rankGroups[r]
                    if (cardsOfRank == null || cardsOfRank.size < 2) { ok = false; break }
                    dblCards.addAll(cardsOfRank.take(2))
                }
                if (ok && dblCards.isNotEmpty())
                    detectPlay(dblCards)?.let { plays.add(it) }
            }
        }

        // Triple straights (2+)
        for (len in 2..6) {
            for (startRank in 0..(12 - len)) {
                val tripCards = mutableListOf<Card>()
                var ok = true
                for (r in startRank until startRank + len) {
                    val cardsOfRank = rankGroups[r]
                    if (cardsOfRank == null || cardsOfRank.size < 3) { ok = false; break }
                    tripCards.addAll(cardsOfRank.take(3))
                }
                if (ok && tripCards.isNotEmpty())
                    detectPlay(tripCards)?.let { plays.add(it) }
            }
        }

        // Four + 2
        for ((rank, cards) in rankGroups) {
            if (cards.size >= 4) {
                val four = cards.take(4)
                val others = sorted.filter { it.rank != rank }
                if (others.size >= 2) {
                    detectPlay(four + others.take(2))?.let { plays.add(it) }
                }
            }
        }

        return plays.distinctBy { it.cards.map { c -> c.id }.sorted() }
    }
}
