package com.trading.monitor.market

import com.trading.monitor.matrix.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class MarketState(
    val price: Double? = null,
    val change24hPct: Double? = null,
    val sma50: Double? = null,
    val sma200: Double? = null,
    /** Latest perpetual funding rate per 8 hours, as a fraction (0.0001 = 0.01%). */
    val fundingRate: Double? = null,
    val fearGreed: Int? = null,
    val fearGreedLabel: String? = null,
    val updatedMs: Long = 0,
    val error: String? = null,
)

/** Free public market data: Binance (price, candles, funding) and alternative.me (Fear & Greed). */
class Market(private val http: OkHttpClient) {
    val state = MutableStateFlow(MarketState())
    private var slowDataAtMs = 0L

    private fun get(url: String): String {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from ${response.request.url.host}")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        var s = state.value
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()

        try {
            val j = JSONObject(get("https://api.binance.com/api/v3/ticker/24hr?symbol=BTCUSDT"))
            s = s.copy(
                price = j.getString("lastPrice").toDouble(),
                change24hPct = j.getString("priceChangePercent").toDouble(),
            )
        } catch (e: Exception) {
            errors += "price: ${e.message}"
        }

        try {
            val j = JSONObject(get("https://fapi.binance.com/fapi/v1/premiumIndex?symbol=BTCUSDT"))
            s = s.copy(fundingRate = j.getString("lastFundingRate").toDouble())
        } catch (e: Exception) {
            errors += "funding: ${e.message}"
        }

        // Daily averages and the sentiment index change slowly: refresh them hourly.
        if (now - slowDataAtMs > 3_600_000) {
            var ok = true
            try {
                val closes = klines("1d", null, 200).map { it.close }
                s = s.copy(
                    sma50 = if (closes.size >= 50) closes.takeLast(50).average() else null,
                    sma200 = if (closes.size >= 200) closes.average() else null,
                )
            } catch (e: Exception) {
                ok = false
                errors += "averages: ${e.message}"
            }
            try {
                val d = JSONObject(get("https://api.alternative.me/fng/?limit=1")).getJSONArray("data").getJSONObject(0)
                s = s.copy(fearGreed = d.getString("value").toInt(), fearGreedLabel = d.optString("value_classification"))
            } catch (e: Exception) {
                ok = false
                errors += "fear & greed: ${e.message}"
            }
            if (ok) slowDataAtMs = now
        }

        state.value = s.copy(updatedMs = now, error = errors.joinToString("; ").ifEmpty { null })
    }

    /** Blocking; call from a background thread. */
    fun klines(interval: String, startMs: Long?, limit: Int): List<Candle> {
        val start = if (startMs != null) "&startTime=$startMs" else ""
        val arr = JSONArray(get("https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=$interval&limit=$limit$start"))
        return (0 until arr.length()).map {
            val k = arr.getJSONArray(it)
            Candle(k.getLong(0), k.getString(2).toDouble(), k.getString(3).toDouble(), k.getString(4).toDouble())
        }
    }
}
