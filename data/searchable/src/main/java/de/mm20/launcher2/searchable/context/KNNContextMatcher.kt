package de.mm20.launcher2.searchable.context

import kotlinx.serialization.Serializable

/**
 * K-Nearest Neighbors context matcher for smart app suggestions.
 * Uses vector distance calculations to find similar historical contexts.
 */
class KNNContextMatcher(
    private val k: Int = 10,  // Number of nearest neighbors to consider
    private val alpha: Double = 0.8  // Weight for KNN vs base score (0.0 = only base, 1.0 = only KNN)
) {
    
    /**
     * Represents a historical app usage with its context vector.
     */
    @Serializable
    data class AppUsageVector(
        val appKey: String,
        val contextVector: ContextVector,
        val timestamp: Long,
        val weight: Double = 1.0  // Usage weight for this particular launch
    )
    
    /**
     * Result of KNN matching with similarity scores and app frequencies.
     */
    data class KNNResult(
        val appKey: String,
        val knnScore: Double,          // Score based on KNN frequency
        val averageSimilarity: Double, // Average similarity to nearest contexts
        val nearestCount: Int,         // How many times app appeared in K nearest
        val totalNearestContexts: Int  // Total K nearest contexts found
    )
    
    /**
     * Find the most relevant apps based on current context using KNN.
     * 
     * @param currentContext Current device/user context
     * @param historicalUsages All historical app usages with context
     * @return List of apps ranked by KNN relevance
     */
    fun findMostRelevantApps(
        currentContext: ContextData,
        historicalUsages: List<AppUsageVector>
    ): List<KNNResult> {
        if (historicalUsages.isEmpty()) return emptyList()
        
        val currentVector = ContextVector.fromContextData(currentContext)
        
        // Find K nearest historical contexts
        val nearestContexts = findKNearestContexts(currentVector, historicalUsages)
        
        // Count app frequency in nearest contexts
        val appFrequencies = countAppFrequencies(nearestContexts)
        
        // Calculate KNN scores for each app
        return appFrequencies.map { (appKey, frequency) ->
            val appUsagesInNearest = nearestContexts.filter { it.appKey == appKey }
            val averageSimilarity = if (appUsagesInNearest.isNotEmpty()) {
                appUsagesInNearest.map { 
                    currentVector.similarityTo(it.contextVector) 
                }.average()
            } else 0.0
            
            val knnScore = calculateKNNScore(frequency, nearestContexts.size, averageSimilarity)
            
            KNNResult(
                appKey = appKey,
                knnScore = knnScore,
                averageSimilarity = averageSimilarity,
                nearestCount = frequency,
                totalNearestContexts = nearestContexts.size
            )
        }.sortedByDescending { it.knnScore }
    }
    
    /**
     * Calculate combined score using KNN and base weight.
     * 
     * @param knnScore Score from KNN algorithm (0.0 to 1.0)
     * @param baseWeight Traditional weight-based score
     * @return Combined score using alpha blending
     */
    fun calculateCombinedScore(knnScore: Double, baseWeight: Double): Double {
        return alpha * knnScore + (1.0 - alpha) * normalizeBaseWeight(baseWeight)
    }
    
    /**
     * Get debug information about KNN matching for a specific app.
     */
    fun getKNNDebugInfo(
        appKey: String,
        currentContext: ContextData,
        appUsages: List<AppUsageVector>
    ): KNNDebugInfo? {
        val appSpecificUsages = appUsages.filter { it.appKey == appKey }
        if (appSpecificUsages.isEmpty()) return null
        
        val currentVector = ContextVector.fromContextData(currentContext)
        val nearestContexts = findKNearestContexts(currentVector, appUsages)
        val appInNearest = nearestContexts.filter { it.appKey == appKey }
        
        return KNNDebugInfo(
            appKey = appKey,
            totalHistoricalUsages = appSpecificUsages.size,
            nearestContextsConsidered = nearestContexts.size,
            appOccurrencesInNearest = appInNearest.size,
            averageDistanceToNearest = if (appInNearest.isNotEmpty()) {
                appInNearest.map { currentVector.distanceTo(it.contextVector) }.average()
            } else Double.MAX_VALUE,
            nearestContextTimestamps = appInNearest.map { it.timestamp }.sorted(),
            contextDimensionsContributing = analyzeContextDimensions(currentVector, appInNearest)
        )
    }
    
    /**
     * Find K nearest historical contexts to the current context.
     */
    private fun findKNearestContexts(
        currentVector: ContextVector,
        historicalUsages: List<AppUsageVector>
    ): List<AppUsageVector> {
        return historicalUsages
            .map { usage ->
                usage to currentVector.distanceTo(usage.contextVector)
            }
            .sortedBy { it.second } // Sort by distance (lower = more similar)
            .take(k)
            .map { it.first }
    }
    
    /**
     * Count how frequently each app appears in the nearest contexts.
     */
    private fun countAppFrequencies(nearestContexts: List<AppUsageVector>): Map<String, Int> {
        return nearestContexts
            .groupBy { it.appKey }
            .mapValues { it.value.size }
    }
    
    /**
     * Calculate KNN score based on frequency and similarity.
     * Used when comparing ACROSS multiple apps.
     */
    private fun calculateKNNScore(
        frequency: Int,
        totalNearestContexts: Int,
        averageSimilarity: Double
    ): Double {
        if (totalNearestContexts == 0) return 0.0

        val frequencyScore = frequency.toDouble() / totalNearestContexts.toDouble()
        val similarityBoost = 1.0 + (averageSimilarity * 0.5) // Up to 50% boost for high similarity

        return frequencyScore * similarityBoost
    }

    /**
     * Calculate context similarity score for a single app.
     * Used when evaluating how well an app's usage history matches the current context.
     * Returns 0.0 to 1.0 based on how similar the current context is to the app's
     * K nearest historical usage contexts.
     */
    fun calculateAppContextScore(
        currentContext: ContextData,
        appUsages: List<AppUsageVector>
    ): Double {
        if (appUsages.isEmpty()) return 0.0

        val currentVector = ContextVector.fromContextData(currentContext)

        // Find K nearest contexts from this app's history
        val nearestContexts = appUsages
            .map { usage -> usage to currentVector.similarityTo(usage.contextVector) }
            .sortedByDescending { it.second } // Sort by similarity (higher = better)
            .take(k)

        if (nearestContexts.isEmpty()) return 0.0

        // Calculate weighted average similarity
        // Give more weight to the closest matches
        var weightedSum = 0.0
        var weightSum = 0.0
        nearestContexts.forEachIndexed { index, (_, similarity) ->
            val weight = 1.0 / (index + 1) // First match gets weight 1.0, second 0.5, third 0.33, etc.
            weightedSum += similarity * weight
            weightSum += weight
        }

        return if (weightSum > 0) weightedSum / weightSum else 0.0
    }
    
    /**
     * Normalize base weight to 0.0-1.0 range for combining with KNN score.
     */
    private fun normalizeBaseWeight(baseWeight: Double): Double {
        // Most weights are between 0.0 and 1.0, but can be higher
        return kotlin.math.min(1.0, kotlin.math.max(0.0, baseWeight))
    }
    
    /**
     * Analyze which context dimensions are most important for matching.
     */
    private fun analyzeContextDimensions(
        currentVector: ContextVector,
        matchingUsages: List<AppUsageVector>
    ): Map<String, Double> {
        if (matchingUsages.isEmpty()) return emptyMap()
        
        // Calculate average difference for each dimension
        val dimensions = mapOf(
            "hour" to matchingUsages.map { kotlin.math.abs(currentVector.hour - it.contextVector.hour) }.average(),
            "dayOfWeek" to matchingUsages.map { kotlin.math.abs(currentVector.dayOfWeek - it.contextVector.dayOfWeek) }.average(),
            "networkType" to matchingUsages.map { kotlin.math.abs(currentVector.networkType - it.contextVector.networkType) }.average(),
            "wifiNetwork" to matchingUsages.map { kotlin.math.abs(currentVector.wifiNetworkHash - it.contextVector.wifiNetworkHash) }.average(),
            "charging" to matchingUsages.map { kotlin.math.abs(currentVector.isCharging - it.contextVector.isCharging) }.average(),
            "orientation" to matchingUsages.map { kotlin.math.abs(currentVector.isPortrait - it.contextVector.isPortrait) }.average()
        )
        
        // Convert differences to similarity scores (lower difference = higher similarity)
        return dimensions.mapValues { (_, avgDifference) ->
            kotlin.math.max(0.0, 1.0 - avgDifference)
        }
    }
    
    /**
     * Debug information for KNN matching.
     */
    data class KNNDebugInfo(
        val appKey: String,
        val totalHistoricalUsages: Int,
        val nearestContextsConsidered: Int,
        val appOccurrencesInNearest: Int,
        val averageDistanceToNearest: Double,
        val nearestContextTimestamps: List<Long>,
        val contextDimensionsContributing: Map<String, Double>
    )
}