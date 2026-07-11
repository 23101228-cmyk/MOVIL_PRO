package com.exchangepro.moviles.data.ai

import com.exchangepro.moviles.BuildConfig
import com.exchangepro.moviles.data.repository.AdminDashboardData
import com.exchangepro.moviles.data.repository.AdminDisputeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GeminiAdminResult(
    val text: String,
    val generatedByGemini: Boolean,
    val errorMessage: String? = null
)

class GeminiAdminService {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeDashboard(data: AdminDashboardData, fallback: String): GeminiAdminResult {
        val prompt = """
            Eres un asistente de analisis administrativo para una app P2P de cambio de divisas.
            Responde en espanol, breve y accionable. No inventes datos.

            Metricas agregadas:
            - Usuarios registrados: ${data.users}
            - Ofertas activas: ${data.activeOffers}
            - Transacciones completadas: ${data.completedTransactions}
            - Disputas pendientes: ${data.pendingDisputes}
            - Disputas resueltas: ${data.resolvedDisputes}
            - Feedback pendiente: ${data.pendingFeedback}
            - Transacciones con pago externo: ${data.externalPaymentTransactions}
            - Transacciones con Wallet Interna: ${data.internalWalletTransactions}
            - Estados de transacciones: ${data.transactionsByStatus}
            - Volumen por moneda: ${data.volumeByCurrency}

            Formato exacto:
            Resumen: una frase.
            Alertas:
            - maximo 2 alertas.
            Recomendaciones:
            - maximo 2 recomendaciones.
        """.trimIndent()

        return generate(prompt, fallback)
    }

    suspend fun analyzeDispute(dispute: AdminDisputeRecord, fallback: String): GeminiAdminResult {
        val prompt = """
            Eres un asistente de soporte para un administrador que resuelve disputas P2P.
            No tomes la decision final; solo resume riesgo y pasos de verificacion.
            Responde en espanol, breve y accionable.

            Disputa:
            - Codigo publico de transaccion: ${dispute.transactionCode}
            - Motivo: ${dispute.reason}
            - Descripcion: ${dispute.description}
            - Monto retenido: ${dispute.amount} ${dispute.currency}
            - Estado: ${dispute.status}
            - Evidencias adjuntas: ${dispute.evidenceCount}

            Formato exacto:
            Riesgo: Bajo, Medio o Alto, con una razon corta.
            Lectura del caso: una frase.
            Sugerencia: un paso concreto para el administrador.
        """.trimIndent()

        return generate(prompt, fallback)
    }

    private suspend fun generate(prompt: String, fallback: String): GeminiAdminResult {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank()) {
            return GeminiAdminResult(
                text = fallback,
                generatedByGemini = false,
                errorMessage = "No se encontro GEMINI_API_KEY en local.properties."
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = JSONObject()
                    .put(
                        "contents",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put(
                                        "parts",
                                        JSONArray()
                                            .put(JSONObject().put("text", prompt))
                                    )
                            )
                    )

                val request = Request.Builder()
                    .url(
                        "https://generativelanguage.googleapis.com/v1beta/models/" +
                                "gemini-flash-lite-latest:generateContent?key=$apiKey"
                    )
                    .post(bodyJson.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()

                    if (!response.isSuccessful) {
                        throw IllegalStateException("Gemini respondio ${response.code}: $responseBody")
                    }

                    val json = JSONObject(responseBody)
                    val text = json
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    require(text.isNotBlank()) { "Gemini no devolvio texto." }

                    GeminiAdminResult(
                        text = text,
                        generatedByGemini = true
                    )
                }
            }.getOrElse { error ->
                GeminiAdminResult(
                    text = fallback,
                    generatedByGemini = false,
                    errorMessage = error.message ?: "No se pudo conectar con Gemini."
                )
            }
        }
    }
}