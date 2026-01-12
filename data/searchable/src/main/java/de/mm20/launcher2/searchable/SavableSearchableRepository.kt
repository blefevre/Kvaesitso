package de.mm20.launcher2.searchable

import android.util.Log
import androidx.room.withTransaction
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdateContentEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdatePinEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.preferences.WeightFactor
import de.mm20.launcher2.preferences.search.FavoritesSettings
import de.mm20.launcher2.preferences.search.RankingSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.searchable.context.ContextData
import de.mm20.launcher2.searchable.context.ContextManager
import de.mm20.launcher2.searchable.context.ContextVector
import de.mm20.launcher2.searchable.context.KNNContextMatcher
import de.mm20.launcher2.searchable.debug.AppDebugInfo
import de.mm20.launcher2.searchable.debug.AppSuggestionDebugInfo
import de.mm20.launcher2.searchable.debug.ContextDebugInfo
import de.mm20.launcher2.searchable.debug.SmartFavoritesAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.mm20.launcher2.searchable.debug.NetworkUsage
import de.mm20.launcher2.searchable.debug.PatternType
import de.mm20.launcher2.searchable.debug.UsagePattern
import org.json.JSONArray
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.error.InstanceCreationException
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.qualifier.named
import java.io.File

interface SavableSearchableRepository : Backupable {

    fun insert(
        searchable: SavableSearchable,
    )

    fun upsert(
        searchable: SavableSearchable,
        visibility: VisibilityLevel? = null,
        pinned: Boolean? = null,
        launchCount: Int? = null,
        weight: Double? = null,
    )

    fun update(
        searchable: SavableSearchable,
        visibility: VisibilityLevel? = null,
        pinned: Boolean? = null,
        launchCount: Int? = null,
        weight: Double? = null,
    )

    /**
     * Replace a searchable in the database.
     * The new entry will inherit the visibility, launch count, weight and pin position of the old entry,
     * but it will have a different key and searchable.
     */
    fun replace(
        key: String,
        newSearchable: SavableSearchable,
    )

    /**
     * Touch a searchable to update its weight and launch counter
     **/
    fun touch(
        searchable: SavableSearchable,
    )

    /**
     * @param minVisibility the minimum visibility of the searchables to return. A visible is
     * considered to be "lower" when it makes an item less visible.
     * @param maxVisibility the maximum visibility of the searchables to return. A visible is
     * considered to be "higher" when it makes an item more visible.
     */
    fun get(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        minPinnedLevel: PinnedLevel = PinnedLevel.NotPinned,
        maxPinnedLevel: PinnedLevel = PinnedLevel.ManuallySorted,
        minVisibility: VisibilityLevel = VisibilityLevel.Hidden,
        maxVisibility: VisibilityLevel = VisibilityLevel.Default,
        limit: Int = 9999,
    ): Flow<List<SavableSearchable>>

    fun getKeys(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        minPinnedLevel: PinnedLevel = PinnedLevel.NotPinned,
        maxPinnedLevel: PinnedLevel = PinnedLevel.ManuallySorted,
        minVisibility: VisibilityLevel = VisibilityLevel.Hidden,
        maxVisibility: VisibilityLevel = VisibilityLevel.Default,
        limit: Int = 9999,
    ): Flow<List<String>>


    fun isPinned(searchable: SavableSearchable): Flow<Boolean>
    fun getVisibility(searchable: SavableSearchable): Flow<VisibilityLevel>
    fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>,
    )

    /**
     * Returns the given keys sorted by relevance.
     * The first item in the list is the most relevant.
     * Unknown keys will not be included in the result.
     */
    fun sortByRelevance(keys: List<String>): Flow<List<String>>

    fun sortByWeight(keys: List<String>): Flow<List<String>>

    fun getWeights(keys: List<String>): Flow<Map<String, Double>>

    /**
     * Remove this item from the Searchable database
     */
    fun delete(searchable: SavableSearchable)

    /**
     * Get items with the given keys from the favorites database.
     * Items that don't exist in the database will not be returned.
     */
    fun getByKeys(keys: List<String>): Flow<List<SavableSearchable>>

    /**
     * Remove database entries that are invalid. This includes
     * - entries that cannot be deserialized anymore
     * - entries that are inconsistent (the key column is not equal to the key of the searchable)
     */
    suspend fun cleanupDatabase(): Int

    /**
     * Debug methods for smart favorites visualization and analysis
     */
    suspend fun getDebugInfo(appKey: String): AppDebugInfo?
    
    suspend fun getAllAppsDebugInfo(): List<AppDebugInfo>
    
    suspend fun getCurrentContextDebugInfo(): ContextDebugInfo
    
    suspend fun getSmartFavoritesAnalytics(): SmartFavoritesAnalytics
    
    suspend fun getAppSuggestionDebugInfo(currentContext: ContextData? = null): List<AppSuggestionDebugInfo>
    
    suspend fun getKnnConfiguration(): Pair<Int, Double>
    
    /**
     * Get frequently used apps sorted by context-aware relevance
     */
    fun getContextAwareFavorites(
        excludeTypes: List<String>? = null,
        limit: Int = 50
    ): Flow<List<SavableSearchable>>
    
    /**
     * Trigger a context refresh to update context-aware favorites
     */
    suspend fun refreshContext()
    
    /**
     * Get reactive app suggestions that update with context changes
     */
    fun getReactiveAppSuggestions(): Flow<List<AppSuggestionDebugInfo>>
    
}

internal class SavableSearchableRepositoryImpl(
    private val database: AppDatabase,
    private val settings: RankingSettings,
    private val favoritesSettings: FavoritesSettings,
    private val contextManager: ContextManager,
) : SavableSearchableRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun insert(searchable: SavableSearchable) {
        val dao = database.searchableDao()
        scope.launch {
            dao.insert(
                SavedSearchableEntity(
                    key = searchable.key,
                    type = searchable.domain,
                    serializedSearchable = searchable.serialize() ?: return@launch,
                    visibility = VisibilityLevel.Default.value,
                    launchCount = 0,
                    weight = 0.0,
                    pinPosition = 0,
                )
            )
        }
    }


    override fun upsert(
        searchable: SavableSearchable,
        visibility: VisibilityLevel?,
        pinned: Boolean?,
        launchCount: Int?,
        weight: Double?
    ) {
        val dao = database.searchableDao()
        scope.launch {
            val entity = dao.getByKey(searchable.key).firstOrNull()
            dao.upsert(
                SavedSearchableEntity(
                    key = searchable.key,
                    type = searchable.domain,
                    visibility = visibility?.value ?: entity?.visibility ?: 0,
                    pinPosition = pinned?.let { if (it) 1 else 0 } ?: entity?.pinPosition ?: 0,
                    launchCount = launchCount ?: entity?.launchCount ?: 0,
                    weight = weight ?: entity?.weight ?: 0.0,
                    serializedSearchable = searchable.serialize() ?: return@launch,
                )
            )
        }
    }

    override fun update(
        searchable: SavableSearchable,
        visibility: VisibilityLevel?,
        pinned: Boolean?,
        launchCount: Int?,
        weight: Double?
    ) {
        val dao = database.searchableDao()
        scope.launch {
            val entity = dao.getByKey(searchable.key).firstOrNull()
            dao.upsert(
                SavedSearchableEntity(
                    key = searchable.key,
                    type = searchable.domain,
                    visibility = visibility?.value ?: entity?.visibility ?: 0,
                    pinPosition = pinned?.let { if (it) 1 else 0 } ?: entity?.pinPosition ?: 0,
                    launchCount = launchCount ?: entity?.launchCount ?: 0,
                    weight = weight ?: entity?.weight ?: 0.0,
                    serializedSearchable = searchable.serialize() ?: return@launch,
                )
            )
        }
    }

    override fun touch(searchable: SavableSearchable) {
        scope.launch {
            val dao = database.searchableDao()
            val currentContext = contextManager.getCurrentContext()
            val isSmartFavoritesEnabled = favoritesSettings.firstOrNull()?.smartEnabled ?: false
            
            val weightFactor =
                when (settings.weightFactor.firstOrNull()) {
                    WeightFactor.Low -> WEIGHT_FACTOR_LOW
                    WeightFactor.High -> WEIGHT_FACTOR_HIGH
                    else -> WEIGHT_FACTOR_MEDIUM
                }

            val item =
                SavedSearchable(searchable.key, searchable, 0, 0, VisibilityLevel.Default, 0.0)
            item.toDatabaseEntity()?.let { entity ->
                if (isSmartFavoritesEnabled) {
                    // Get existing context history
                    val existingHistory = dao.getContextHistory(searchable.key)
                    val historyList = existingHistory?.let { 
                        try {
                            Json.decodeFromString<List<ContextData>>(it)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } ?: emptyList()
                    
                    // Add current context to history
                    val updatedHistory = (historyList + currentContext).takeLast(MAX_CONTEXT_HISTORY)
                    val historyJson = Json.encodeToString(updatedHistory)
                    
                    // Use the standard weight factor for EMA updates
                    // KNN scores are calculated at display time, not stored in the weight
                    // This ensures consistent weight decay regardless of context matching
                    dao.touch(entity, weightFactor)
                    dao.updateContextHistory(searchable.key, historyJson)
                    
                } else {
                    // Standard touch operation without context awareness
                    dao.touch(entity, weightFactor)
                }
            }
        }
    }

    override fun get(
        includeTypes: List<String>?,
        excludeTypes: List<String>?,
        minPinnedLevel: PinnedLevel,
        maxPinnedLevel: PinnedLevel,
        minVisibility: VisibilityLevel,
        maxVisibility: VisibilityLevel,
        limit: Int
    ): Flow<List<SavableSearchable>> {
        val dao = database.searchableDao()
        val entities = when {
            includeTypes == null && excludeTypes == null -> dao.get(
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            includeTypes == null -> dao.getExcludeTypes(
                excludeTypes = excludeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            excludeTypes == null -> dao.getIncludeTypes(
                includeTypes = includeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            else -> throw IllegalArgumentException("Cannot specify both includeTypes and excludeTypes")
        }

        return entities.map {
            it.mapNotNull { fromDatabaseEntity(it).searchable }
        }
    }

    override fun getKeys(
        includeTypes: List<String>?,
        excludeTypes: List<String>?,
        minPinnedLevel: PinnedLevel,
        maxPinnedLevel: PinnedLevel,
        minVisibility: VisibilityLevel,
        maxVisibility: VisibilityLevel,
        limit: Int
    ): Flow<List<String>> {
        val dao = database.searchableDao()
        return when {
            includeTypes == null && excludeTypes == null -> dao.getKeys(
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            includeTypes == null -> dao.getKeysExcludeTypes(
                excludeTypes = excludeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            excludeTypes == null -> dao.getKeysIncludeTypes(
                includeTypes = includeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            else -> throw IllegalArgumentException("Cannot specify both includeTypes and excludeTypes")
        }
    }

    override fun isPinned(searchable: SavableSearchable): Flow<Boolean> {
        return database.searchableDao().isPinned(searchable.key)
    }

    override fun getVisibility(searchable: SavableSearchable): Flow<VisibilityLevel> {
        return database.searchableDao().getVisibility(searchable.key).map {
            VisibilityLevel.fromInt(it)
        }
    }

    override fun delete(searchable: SavableSearchable) {
        scope.launch {
            database.searchableDao().delete(searchable.key)
        }
    }

    override fun replace(key: String, newSearchable: SavableSearchable) {
        scope.launch {
            database.searchableDao().replace(
                key,
                SavedSearchableUpdateContentEntity(
                    key = newSearchable.key,
                    type = newSearchable.domain,
                    serializedSearchable = newSearchable.serialize() ?: return@launch
                )
            )
        }
    }

    override fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>
    ) {
        val dao = database.searchableDao()
        scope.launch {
            database.withTransaction {
                dao.unpinAll()
                dao.upsert(
                    manuallySorted.mapIndexedNotNull { index, savableSearchable ->
                        SavedSearchableUpdatePinEntity(
                            key = savableSearchable.key,
                            type = savableSearchable.domain,
                            pinPosition = manuallySorted.size - index + 1,
                            serializedSearchable = savableSearchable.serialize()
                                ?: return@mapIndexedNotNull null,
                        )
                    }
                )
                dao.upsert(
                    automaticallySorted.mapNotNull { savableSearchable ->
                        SavedSearchableUpdatePinEntity(
                            key = savableSearchable.key,
                            type = savableSearchable.domain,
                            pinPosition = 1,
                            serializedSearchable = savableSearchable.serialize()
                                ?: return@mapNotNull null,
                        )
                    }
                )
            }
        }
    }

    override fun sortByRelevance(keys: List<String>): Flow<List<String>> {
        if (keys.size > 999) return flowOf(emptyList())
        return database.searchableDao().sortByRelevance(keys)
    }

    override fun sortByWeight(keys: List<String>): Flow<List<String>> {
        if (keys.size > 999) return flowOf(emptyList())
        return database.searchableDao().sortByWeight(keys)
    }

    override fun getWeights(keys: List<String>): Flow<Map<String, Double>> {
        if (keys.size > 999) return flowOf(emptyMap())
        return database.searchableDao().getWeights(keys)
    }

    private suspend fun fromDatabaseEntity(entity: SavedSearchableEntity): SavedSearchable {
        val deserializer: SearchableDeserializer? = try {
            get(named(entity.type))
        } catch (e: NoDefinitionFoundException) {
            CrashReporter.logException(e)
            null
        } catch (e: InstanceCreationException) {
            CrashReporter.logException(e)
            null
        }
        val searchable = deserializer?.deserialize(entity.serializedSearchable)
        if (searchable == null) removeInvalidItem(entity.key)
        return SavedSearchable(
            key = entity.key,
            searchable = searchable,
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            visibility = VisibilityLevel.fromInt(entity.visibility),
            weight = entity.weight
        )
    }

    private fun removeInvalidItem(key: String) {
        scope.launch {
            database.searchableDao().delete(key)
        }
    }

    override fun getByKeys(keys: List<String>): Flow<List<SavableSearchable>> {
        val dao = database.searchableDao()
        if (keys.size > 999) {
            return combine(keys.chunked(999).map {
                dao.getByKeys(it)
                    .map {
                        it.mapNotNull { fromDatabaseEntity(it).searchable }
                    }
            }) { results ->
                results.flatMap { it }
            }
        }
        return dao.getByKeys(keys)
            .map { it.mapNotNull { fromDatabaseEntity(it).searchable } }
    }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        var page = 0
        do {
            val favorites = dao.exportFavorites(limit = 100, offset = page * 100)
            val jsonArray = JSONArray()
            for (fav in favorites) {
                jsonArray.put(
                    jsonObjectOf(
                        "key" to fav.key,
                        "type" to fav.type,
                        "visibility" to fav.visibility,
                        "launchCount" to fav.launchCount,
                        "pinPosition" to fav.pinPosition,
                        "searchable" to fav.serializedSearchable,
                        "weight" to fav.weight,
                    )
                )
            }

            val file = File(toDir, "favorites.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
            page++
        } while (favorites.size == 100)
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        dao.wipeFavorites()

        val files =
            fromDir.listFiles { _, name -> name.startsWith("favorites.") } ?: return@withContext

        for (file in files) {
            val favorites = mutableListOf<SavedSearchableEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val entity = SavedSearchableEntity(
                        key = json.getString("key"),
                        type = json.optString("type").takeIf { it.isNotEmpty() } ?: continue,
                        serializedSearchable = json.getString("searchable"),
                        launchCount = json.getInt("launchCount"),
                        visibility = json.optInt("visibility", 0),
                        pinPosition = json.getInt("pinPosition"),
                        weight = json.optDouble("weight").takeIf { !it.isNaN() } ?: 0.0
                    )
                    favorites.add(entity)
                }

                dao.importFavorites(favorites)

            } catch (e: JSONException) {
                CrashReporter.logException(e)
            }
        }
    }

    override suspend fun cleanupDatabase(): Int {
        var removed = 0
        val job = scope.launch {
            val dao = database.backupDao()
            var page = 0
            do {
                val favorites = dao.exportFavorites(limit = 100, offset = page * 100)
                for (fav in favorites) {
                    val item = fromDatabaseEntity(fav)
                    if (item.searchable == null || item.searchable.key != item.key) {
                        removeInvalidItem(item.key)
                        removed++
                        Log.i(
                            "MM20",
                            "SearchableDatabase cleanup: removed invalid item ${item.key}"
                        )
                    }
                }
                page++
            } while (favorites.size == 100)
        }
        job.join()
        return removed
    }

    // Debug methods implementation
    override suspend fun getDebugInfo(appKey: String): AppDebugInfo? {
        val dao = database.searchableDao()
        val entity = dao.getByKey(appKey).firstOrNull() ?: return null
        
        val contextHistory = entity.contextHistory.let { historyJson ->
            try {
                Json.decodeFromString<List<ContextData>>(historyJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        val patterns = analyzeUsagePatternsForApp(contextHistory)
        val currentContext = contextManager.getCurrentContext()
        val contextScore = if (contextHistory.isNotEmpty()) {
            contextManager.calculateContextAwareWeight(contextHistory, currentContext, entity.weight)
        } else {
            entity.weight
        }
        
        return AppDebugInfo(
            key = entity.key,
            name = extractAppNameFromEntity(entity),
            launchCount = entity.launchCount,
            weight = entity.weight,
            contextHistory = contextHistory,
            patterns = patterns,
            currentContextScore = contextScore,
            lastLaunched = contextHistory.lastOrNull()?.timestamp
        )
    }
    
    override suspend fun getAllAppsDebugInfo(): List<AppDebugInfo> {
        val dao = database.searchableDao()
        val allEntities = dao.get(
            frequentlyUsed = true,
            manuallySorted = true,
            automaticallySorted = true,
            unused = true,
            limit = 1000
        ).firstOrNull() ?: emptyList()
        
        return allEntities.mapNotNull { entity ->
            getDebugInfo(entity.key)
        }.sortedByDescending { it.weight }
    }
    
    override suspend fun getCurrentContextDebugInfo(): ContextDebugInfo {
        val currentContext = contextManager.getCurrentContext()
        return ContextDebugInfo(
            currentContext = currentContext,
            timestamp = System.currentTimeMillis(),
            contextSummary = buildContextSummary(currentContext)
        )
    }
    
    override suspend fun getSmartFavoritesAnalytics(): SmartFavoritesAnalytics {
        val dao = database.searchableDao()
        val allEntities = dao.get(
            frequentlyUsed = true,
            manuallySorted = true,
            automaticallySorted = true,
            unused = true,
            limit = 1000
        ).firstOrNull() ?: emptyList()
        
        val appsWithData = allEntities.filter { it.contextHistory.isNotBlank() && it.contextHistory != "[]" }
        
        val allContextHistory = appsWithData.flatMap { entity ->
            try {
                Json.decodeFromString<List<ContextData>>(entity.contextHistory)
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        val timeSlotCounts = allContextHistory.mapNotNull { it.timeContext?.timeSlot }
            .groupingBy { it }.eachCount()
        val mostActiveTimeSlot = timeSlotCounts.maxByOrNull { it.value }?.key
        
        val wifiUsage = allContextHistory.mapNotNull { it.networkContext?.wifiSsid }
            .groupingBy { it }.eachCount()
            .map { (ssid, count) -> NetworkUsage(ssid, count, 1) }
            .sortedByDescending { it.usageCount }
            .take(10)
        
        val bluetoothCategories = allContextHistory.flatMap { 
            it.bluetoothContext?.deviceCategories ?: emptySet() 
        }.map { it.name }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }.take(5).map { it.first }
        
        val batteryUsage = allContextHistory.mapNotNull { it.deviceContext?.isCharging }
            .groupingBy { if (it) "charging" else "not_charging" }.eachCount()
        
        val orientationUsage = allContextHistory.mapNotNull { it.deviceContext?.orientation }
            .groupingBy { it.name }.eachCount()
        
        return SmartFavoritesAnalytics(
            totalApps = allEntities.size,
            appsWithSmartData = appsWithData.size,
            averageContextHistorySize = if (appsWithData.isNotEmpty()) {
                appsWithData.sumOf { entity ->
                    try {
                        Json.decodeFromString<List<ContextData>>(entity.contextHistory).size
                    } catch (e: Exception) {
                        0
                    }
                }.toDouble() / appsWithData.size
            } else 0.0,
            mostActiveTimeSlot = mostActiveTimeSlot,
            topWifiNetworks = wifiUsage,
            topBluetoothCategories = bluetoothCategories,
            batteryUsageDistribution = batteryUsage,
            orientationUsage = orientationUsage
        )
    }
    
    override suspend fun getAppSuggestionDebugInfo(currentContext: ContextData?): List<AppSuggestionDebugInfo> {
        val context = currentContext ?: contextManager.getCurrentContext()
        val dao = database.searchableDao()
        
        val allEntities = dao.get(
            frequentlyUsed = true,
            limit = 50
        ).firstOrNull() ?: emptyList()
        
        // Get KNN configuration - use same settings as getContextAwareFavorites
        val settingsData = favoritesSettings.firstOrNull()
        val knnK = settingsData?.knnK ?: 10
        val knnAlpha = settingsData?.knnAlpha ?: 0.7
        val knnMatcher = KNNContextMatcher(k = knnK, alpha = knnAlpha)
        
        return allEntities.mapNotNull { entity ->
            val contextHistory = try {
                Json.decodeFromString<List<ContextData>>(entity.contextHistory)
            } catch (e: Exception) {
                emptyList()
            }
            
            if (contextHistory.isEmpty()) return@mapNotNull null
            
            // Use the SAME individual scoring logic as getContextAwareFavorites
            val usageVectors = contextHistory.map { contextData ->
                KNNContextMatcher.AppUsageVector(
                    appKey = entity.key,
                    contextVector = ContextVector.fromContextData(contextData),
                    timestamp = contextData.timestamp,
                    weight = 1.0
                )
            }

            // Calculate context similarity score for this app
            val contextSimilarity = knnMatcher.calculateAppContextScore(context, usageVectors)

            // Combine context similarity with base weight (same as getContextAwareFavorites)
            val contextScore = knnMatcher.calculateCombinedScore(contextSimilarity, entity.weight)
            
            // Build matching patterns for debugging
            val patterns = contextManager.analyzeUsagePatterns(contextHistory)
            val matchingPatterns = patterns.filter { pattern ->
                when {
                    pattern.startsWith("time_") -> {
                        val hour = pattern.substringAfter("time_").substringBefore("h").toIntOrNull()
                        hour == context.timeContext?.hour
                    }
                    pattern.startsWith("day_") -> {
                        val day = pattern.substringAfter("day_").toIntOrNull()
                        day == context.timeContext?.dayOfWeek
                    }
                    pattern.startsWith("wifi_") -> {
                        val ssid = pattern.substringAfter("wifi_")
                        ssid == context.networkContext?.wifiSsid
                    }
                    else -> false
                }
            }
            
            AppSuggestionDebugInfo(
                appKey = entity.key,
                appName = extractAppNameFromEntity(entity),
                baseWeight = entity.weight,
                contextBoost = contextScore - entity.weight,
                finalScore = contextScore,
                matchingPatterns = matchingPatterns,
                contextSimilarity = contextSimilarity,
                reason = "Similarity: ${String.format("%.3f", contextSimilarity)}, patterns: ${matchingPatterns.size}"
            )
        }.sortedByDescending { it.finalScore }
    }
    
    // Helper methods
    private fun analyzeUsagePatternsForApp(contextHistory: List<ContextData>): List<UsagePattern> {
        val patterns = mutableListOf<UsagePattern>()
        
        if (contextHistory.size < 3) return patterns
        
        // Time patterns
        val hours = contextHistory.mapNotNull { it.timeContext?.hour }
        val mostCommonHour = hours.groupingBy { it }.eachCount().maxByOrNull { it.value }
        mostCommonHour?.let { (hour, count) ->
            val strength = count.toDouble() / hours.size
            if (strength >= 0.4) {
                patterns.add(
                    UsagePattern(
                        type = PatternType.TIME_OF_DAY,
                        description = "Often used at ${hour}:00",
                        strength = strength,
                        occurrences = count,
                        details = "Used at ${hour}:00 in ${count}/${hours.size} sessions"
                    )
                )
            }
        }
        
        // WiFi patterns
        val wifiNetworks = contextHistory.mapNotNull { it.networkContext?.wifiSsid }
        val mostCommonWifi = wifiNetworks.groupingBy { it }.eachCount().maxByOrNull { it.value }
        mostCommonWifi?.let { (ssid, count) ->
            val strength = count.toDouble() / wifiNetworks.size
            if (strength >= 0.6) {
                patterns.add(
                    UsagePattern(
                        type = PatternType.WIFI_NETWORK,
                        description = "Frequently used on $ssid",
                        strength = strength,
                        occurrences = count,
                        details = "Used on $ssid in ${count}/${wifiNetworks.size} sessions"
                    )
                )
            }
        }
        
        return patterns
    }
    
    private suspend fun extractAppNameFromEntity(entity: SavedSearchableEntity): String {
        return try {
            val savedSearchable = fromDatabaseEntity(entity)
            savedSearchable.searchable?.label ?: "Unknown App"
        } catch (e: Exception) {
            // Fallback to simple string extraction
            try {
                entity.serializedSearchable.substringAfter("\"label\":\"").substringBefore("\"")
            } catch (e2: Exception) {
                "Unknown App"
            }
        }
    }
    
    private fun buildContextSummary(context: ContextData): String {
        val parts = mutableListOf<String>()
        
        context.timeContext?.let { time ->
            parts.add("${time.hour}:00 ${time.timeSlot.name.lowercase()}")
        }
        
        context.networkContext?.let { network ->
            when {
                network.wifiSsid != null -> parts.add("WiFi: ${network.wifiSsid}")
                network.connectionType.name == "MOBILE" -> parts.add("Mobile data")
                else -> parts.add("No connection")
            }
        }
        
        context.deviceContext?.let { device ->
            if (device.isCharging) parts.add("Charging")
            parts.add(device.orientation.name.lowercase())
        }
        
        return parts.joinToString(" • ")
    }
    
    private fun buildSuggestionReason(
        similarity: Double,
        matchingPatterns: List<String>,
        launchCount: Int
    ): String {
        val reasons = mutableListOf<String>()
        
        if (similarity > 0.8) {
            reasons.add("Very similar context")
        } else if (similarity > 0.6) {
            reasons.add("Similar context")
        }
        
        if (matchingPatterns.isNotEmpty()) {
            reasons.add("${matchingPatterns.size} pattern(s) match")
        }
        
        if (launchCount > 10) {
            reasons.add("Frequently used")
        }
        
        return if (reasons.isNotEmpty()) {
            reasons.joinToString(" • ")
        } else {
            "Low similarity score"
        }
    }
    
    private fun buildKNNSuggestionReason(
        knnResult: KNNContextMatcher.KNNResult?,
        matchingPatterns: List<String>,
        launchCount: Int
    ): String {
        val reasons = mutableListOf<String>()
        
        knnResult?.let { result ->
            if (result.knnScore > 0.8) {
                reasons.add("Strong KNN match (${result.nearestCount}/${result.totalNearestContexts})")
            } else if (result.knnScore > 0.4) {
                reasons.add("Good KNN match (${result.nearestCount}/${result.totalNearestContexts})")
            } else if (result.nearestCount > 0) {
                reasons.add("Weak KNN match (${result.nearestCount}/${result.totalNearestContexts})")
            }
            
            if (result.averageSimilarity > 0.8) {
                reasons.add("High context similarity")
            } else if (result.averageSimilarity > 0.6) {
                reasons.add("Good context similarity")
            }
        }
        
        if (matchingPatterns.isNotEmpty()) {
            reasons.add("${matchingPatterns.size} pattern(s) match")
        }
        
        if (launchCount > 10) {
            reasons.add("Frequently used")
        }
        
        return if (reasons.isNotEmpty()) {
            reasons.joinToString(" • ")
        } else {
            "No KNN match found"
        }
    }
    
    override suspend fun getKnnConfiguration(): Pair<Int, Double> {
        val settingsData = favoritesSettings.firstOrNull()
        val knnK = settingsData?.knnK ?: 10
        val knnAlpha = settingsData?.knnAlpha ?: 0.7
        return Pair(knnK, knnAlpha)
    }
    
    /**
     * Internal data class to hold scored app results.
     * Used as single source of truth for both favorites display and debug view.
     */
    private data class ScoredApp(
        val searchable: SavableSearchable,
        val entity: SavedSearchableEntity,
        val baseWeight: Double,
        val contextSimilarity: Double,
        val finalScore: Double
    )

    /**
     * Internal function to calculate scored apps for a given context.
     * This is the SINGLE SOURCE OF TRUTH for scoring - both getContextAwareFavorites
     * and getReactiveAppSuggestions use this.
     */
    private suspend fun calculateScoredApps(
        currentContext: ContextData,
        knnK: Int,
        knnAlpha: Double,
        limit: Int
    ): List<ScoredApp> {
        val dao = database.searchableDao()
        val knnMatcher = KNNContextMatcher(k = knnK, alpha = knnAlpha)

        // Always fetch a large pool of candidates for KNN scoring
        // KNN can completely reorder apps, so we need to consider many candidates
        // even if the final output limit is small
        val candidatePoolSize = maxOf(50, limit * 2)
        val entities = dao.get(
            frequentlyUsed = true,
            limit = candidatePoolSize
        ).firstOrNull() ?: emptyList()

        if (entities.isEmpty()) {
            return emptyList()
        }

        return entities.mapNotNull { entity ->
            val savedSearchable = fromDatabaseEntity(entity)
            val searchable = savedSearchable.searchable ?: return@mapNotNull null

            // Get context history for this app
            val contextHistory = try {
                Json.decodeFromString<List<ContextData>>(entity.contextHistory)
            } catch (e: Exception) {
                emptyList()
            }

            if (contextHistory.isEmpty()) {
                // No context history - use base weight only
                return@mapNotNull ScoredApp(
                    searchable = searchable,
                    entity = entity,
                    baseWeight = entity.weight,
                    contextSimilarity = 0.0,
                    finalScore = entity.weight
                )
            }

            // Convert to usage vectors
            val usageVectors = contextHistory.map { contextData ->
                KNNContextMatcher.AppUsageVector(
                    appKey = entity.key,
                    contextVector = ContextVector.fromContextData(contextData),
                    timestamp = contextData.timestamp,
                    weight = 1.0
                )
            }

            // Calculate context similarity score for this app
            val contextSimilarity = knnMatcher.calculateAppContextScore(currentContext, usageVectors)

            // Combine context similarity with base weight
            val finalScore = knnMatcher.calculateCombinedScore(contextSimilarity, entity.weight)

            ScoredApp(
                searchable = searchable,
                entity = entity,
                baseWeight = entity.weight,
                contextSimilarity = contextSimilarity,
                finalScore = finalScore
            )
        }.sortedByDescending { it.finalScore }.take(limit)
    }

    override fun getContextAwareFavorites(
        excludeTypes: List<String>?,
        limit: Int
    ): Flow<List<SavableSearchable>> = favoritesSettings.flatMapLatest { settings ->
        if (settings?.smartEnabled != true) {
            // If smart favorites is disabled, just return regular frequently used apps
            get(
                excludeTypes = excludeTypes,
                minPinnedLevel = PinnedLevel.FrequentlyUsed,
                maxPinnedLevel = PinnedLevel.FrequentlyUsed,
                limit = limit,
            )
        } else {
            // Smart favorites enabled - sort by context-aware relevance
            contextManager.contextFlow.flatMapLatest { currentContext ->
                flow {
                    android.util.Log.d("ContextAwareFavorites", "Recalculating favorites for context: $currentContext")

                    val knnK = settings.knnK ?: 10
                    val knnAlpha = settings.knnAlpha ?: 0.7

                    val scoredApps = calculateScoredApps(currentContext, knnK, knnAlpha, limit)
                    val sortedApps = scoredApps.map { it.searchable }

                    android.util.Log.d("ContextAwareFavorites", "Emitting ${sortedApps.size} context-aware favorites: ${scoredApps.map { "${it.searchable.key} (${String.format("%.3f", it.finalScore)})" }}")

                    emit(sortedApps)
                }
            }
        }
    }

    override suspend fun refreshContext() {
        contextManager.refreshContext()
    }

    override fun getReactiveAppSuggestions(): Flow<List<AppSuggestionDebugInfo>> {
        return favoritesSettings.flatMapLatest { settings ->
            if (settings?.smartEnabled != true) {
                // Smart favorites disabled - return empty
                flowOf(emptyList())
            } else {
                contextManager.contextFlow.flatMapLatest { currentContext ->
                    flow {
                        android.util.Log.d("ReactiveAppSuggestions", "Creating debug info for context: $currentContext")

                        val knnK = settings.knnK ?: 10
                        val knnAlpha = settings.knnAlpha ?: 0.7

                        // Use the SAME scoring function as getContextAwareFavorites
                        val scoredApps = calculateScoredApps(currentContext, knnK, knnAlpha, 50)

                        val debugInfoList = scoredApps.mapIndexed { index, scored ->
                            val appName = scored.searchable.labelOverride
                                ?: scored.searchable.label

                            AppSuggestionDebugInfo(
                                appKey = scored.entity.key,
                                appName = appName,
                                baseWeight = scored.baseWeight,
                                contextBoost = scored.finalScore - scored.baseWeight,
                                finalScore = scored.finalScore,
                                matchingPatterns = emptyList<String>(),
                                contextSimilarity = scored.contextSimilarity,
                                reason = "Rank #${index + 1}, Similarity: ${String.format("%.3f", scored.contextSimilarity)}, Base: ${String.format("%.3f", scored.baseWeight)}"
                            )
                        }

                        android.util.Log.d("ReactiveAppSuggestions", "Debug info created: ${debugInfoList.map { "${it.appName} (${String.format("%.3f", it.finalScore)})" }}")
                        emit(debugInfoList)
                    }
                }
            }
        }
    }

    companion object {
        private const val WEIGHT_FACTOR_LOW = 0.01
        private const val WEIGHT_FACTOR_MEDIUM = 0.03
        private const val WEIGHT_FACTOR_HIGH = 0.1
        private const val MAX_CONTEXT_HISTORY = 50 // Maximum number of context entries to keep per app
    }
}