package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.domain.model.CurrencyCode
import com.exchangepro.moviles.domain.model.ExchangeRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.round

class ExchangeRateRepository {
    /**
     * Consulta Frankfurter v2 para hoy y ayer. El servicio entrega moneda extranjera
     * por PEN, por eso se invierte la tasa antes de mostrar PEN por moneda.
     */
    suspend fun getRates(): List<ExchangeRate> = withContext(Dispatchers.IO) {
        runCatching {
            val today = LocalDate.now()
            val todayRates = fetchRates(today)
            val yesterdayRates = fetchRates(today.minusDays(1))
            val spreadRate = 0.008

            currencies.mapNotNull { currency ->
                val todayValue = todayRates[currency.name]?.takeIf { it > 0.0 } ?: return@mapNotNull null
                val yesterdayValue = yesterdayRates[currency.name]?.takeIf { it > 0.0 } ?: todayValue
                val mid = round4(1 / todayValue)
                val previousMid = round4(1 / yesterdayValue)
                val direction = when {
                    mid > previousMid -> "sube"
                    mid < previousMid -> "baja"
                    else -> "estable"
                }

                ExchangeRate(
                    code = currency,
                    mid = mid,
                    buy = round3(mid * (1 - spreadRate)),
                    sell = round3(mid * (1 + spreadRate)),
                    direction = direction
                )
            }
        }.getOrElse { fallbackRates() }
    }
    private val currencies = listOf(CurrencyCode.USD, CurrencyCode.EUR, CurrencyCode.GBP, CurrencyCode.JPY)


    private fun fetchRates(date: LocalDate): Map<String, Double> {
        val symbols = currencies.joinToString(",") { it.name }
        val connection = URL(
            "https://api.frankfurter.dev/v2/rates?base=PEN&quotes=$symbols&date=$date"
        )
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000

        return try {
            require(connection.responseCode in 200..299) {
                "Frankfurter respondio HTTP ${connection.responseCode}."
            }
            connection.inputStream.bufferedReader().use { reader ->
                val rates = JSONArray(reader.readText())
                buildMap {
                    for (index in 0 until rates.length()) {
                        val item = rates.getJSONObject(index)
                        put(item.getString("quote"), item.getDouble("rate"))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fallbackRates(): List<ExchangeRate> = listOf(
        fallbackRate(CurrencyCode.USD, 3.410),
        fallbackRate(CurrencyCode.EUR, 3.884),
        fallbackRate(CurrencyCode.GBP, 4.504),
        fallbackRate(CurrencyCode.JPY, 0.021)
    )

    private fun fallbackRate(currency: CurrencyCode, mid: Double): ExchangeRate {
        val spreadRate = 0.008
        return ExchangeRate(
            code = currency,
            mid = mid,
            buy = round3(mid * (1 - spreadRate)),
            sell = round3(mid * (1 + spreadRate)),
            direction = "estable"
        )
    }

    private fun round3(value: Double): Double = round(value * 1000) / 1000

    private fun round4(value: Double): Double = round(value * 10000) / 10000
}
