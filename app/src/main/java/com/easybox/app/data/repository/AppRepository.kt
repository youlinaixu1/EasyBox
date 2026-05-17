package com.easybox.app.data.repository

import com.easybox.app.data.local.PreferencesManager
import com.easybox.app.data.local.dao.AppItemDao
import com.easybox.app.data.model.AppItem
import com.easybox.app.data.model.GameType
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val dao: AppItemDao,
    private val prefs: PreferencesManager
) {
    val appItems: Flow<List<AppItem>> = dao.observeAll()
    val nickname: Flow<String> = prefs.nickname

    suspend fun setNickname(name: String) = prefs.setNickname(name)

    suspend fun initializeBuiltInApps() {
        // Remove flight_chess and military_chess from older versions
        dao.deleteById(GameType.FLIGHT_CHESS.id)
        dao.deleteById(GameType.MILITARY_CHESS.id)

        val existing = dao.getAll()
        if (existing.isNotEmpty()) return

        val builtIn = listOf(
            AppItem(
                id = GameType.CHINESE_CHESS.id,
                name = GameType.CHINESE_CHESS.displayName,
                type = GameType.CHINESE_CHESS.id,
                category = "game",
                iconName = "chess_chinese",
                sortOrder = 0
            ),
            AppItem(
                id = GameType.INTERNATIONAL_CHESS.id,
                name = GameType.INTERNATIONAL_CHESS.displayName,
                type = GameType.INTERNATIONAL_CHESS.id,
                category = "game",
                iconName = "chess_international",
                sortOrder = 1
            ),
            AppItem(
                id = GameType.DOU_DI_ZHU.id,
                name = GameType.DOU_DI_ZHU.displayName,
                type = GameType.DOU_DI_ZHU.id,
                category = "game",
                iconName = "dou_di_zhu",
                sortOrder = 2
            ),
            AppItem(
                id = GameType.MAHJONG.id,
                name = GameType.MAHJONG.displayName,
                type = GameType.MAHJONG.id,
                category = "game",
                iconName = "mahjong",
                sortOrder = 3
            ),
            AppItem(
                id = GameType.SPINNER.id,
                name = GameType.SPINNER.displayName,
                type = GameType.SPINNER.id,
                category = "tool",
                iconName = "spinner",
                sortOrder = 4
            )
        )
        dao.insertAll(builtIn)
    }

    suspend fun updateSortOrders(items: List<AppItem>) {
        items.forEachIndexed { index, item ->
            dao.updateSortOrder(item.id, index)
        }
    }

    suspend fun addAppItem(item: AppItem) = dao.insert(item)
    suspend fun deleteAppItem(id: String) = dao.deleteById(id)
}
