package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class FavoritesSettingsData(
    val columns: Int,
    val frequentlyUsed: Boolean,
    val frequentlyUsedRows: Int,
    val smartEnabled: Boolean,
    val knnK: Int,
    val knnAlpha: Double,
)

class FavoritesSettings internal constructor(
    private val dataStore: LauncherDataStore,
) : Flow<FavoritesSettingsData> by (dataStore.data.map {
    FavoritesSettingsData(
        columns = it.gridColumnCount,
        frequentlyUsed = it.favoritesFrequentlyUsed,
        frequentlyUsedRows = it.favoritesFrequentlyUsedRows,
        smartEnabled = it.favoritesSmartEnabled,
        knnK = it.favoritesKnnK,
        knnAlpha = it.favoritesKnnAlpha,
    )
}.distinctUntilChanged()) {

    val showEditButton
        get() = dataStore.data.map { it.favoritesEditButton }.distinctUntilChanged()

    fun setShowEditButton(showEditButton: Boolean) {
        dataStore.update { it.copy(favoritesEditButton = showEditButton) }
    }

    val frequentlyUsed: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesFrequentlyUsed }.distinctUntilChanged()

    fun setFrequentlyUsed(frequentlyUsed: Boolean) {
        dataStore.update { it.copy(favoritesFrequentlyUsed = frequentlyUsed) }
    }

    val frequentlyUsedRows: Flow<Int>
        get() = dataStore.data.map { it.favoritesFrequentlyUsedRows }.distinctUntilChanged()

    fun setFrequentlyUsedRows(frequentlyUsedRows: Int) {
        dataStore.update { it.copy(favoritesFrequentlyUsedRows = frequentlyUsedRows) }
    }

    val compactTags: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesCompactTags }.distinctUntilChanged()

    fun setCompactTags(compactTags: Boolean) {
        dataStore.update { it.copy(favoritesCompactTags = compactTags) }
    }

    val smartEnabled: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesSmartEnabled }.distinctUntilChanged()

    fun setSmartEnabled(smartEnabled: Boolean) {
        dataStore.update { it.copy(favoritesSmartEnabled = smartEnabled) }
    }

    val knnK: Flow<Int>
        get() = dataStore.data.map { it.favoritesKnnK }.distinctUntilChanged()

    fun setKnnK(knnK: Int) {
        dataStore.update { it.copy(favoritesKnnK = knnK) }
    }

    val knnAlpha: Flow<Double>
        get() = dataStore.data.map { it.favoritesKnnAlpha }.distinctUntilChanged()

    fun setKnnAlpha(knnAlpha: Double) {
        dataStore.update { it.copy(favoritesKnnAlpha = knnAlpha) }
    }
}