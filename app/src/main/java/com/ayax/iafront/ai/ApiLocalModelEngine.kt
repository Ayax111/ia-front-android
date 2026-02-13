package com.ayax.iafront.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiLocalModelEngine(
    baseUrl: String = "http://192.168.0.194:1234"
) : LocalModelEngine {

    private var baseUrl: String = normalizeBaseUrl(baseUrl)
    private var currentModel: String? = null

    override suspend fun initialize(): Boolean {
        val models = listModels()
        if (models.isEmpty()) return false
        if (currentModel == null || !models.contains(currentModel)) {
            currentModel = models.first()
        }
        return true
    }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val response = request("GET", "/v1/models")
        val json = JSONObject(response)
        val data = json.optJSONArray("data") ?: JSONArray()
        buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                if (id.isNotEmpty()) add(id)
            }
        }
    }

    override fun selectModel(modelId: String) {
        currentModel = modelId
    }

    override fun selectedModel(): String? = currentModel

    override fun setBaseUrl(baseUrl: String) {
        this.baseUrl = normalizeBaseUrl(baseUrl)
        currentModel = null
    }

    override fun getBaseUrl(): String = baseUrl

    override suspend fun generateConversationTitle(firstUserPrompt: String): String = withContext(Dispatchers.IO) {
        val model = currentModel ?: return@withContext firstUserPrompt.trim().take(42)
        val payload = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("messages", JSONArray()
                .put(JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Genera un titulo corto en espanol (maximo 6 palabras), sin comillas ni punto final."
                    )
                )
                .put(JSONObject()
                    .put("role", "user")
                    .put("content", firstUserPrompt)
                )
            )

        val response = request("POST", "/v1/chat/completions", payload.toString())
        val json = JSONObject(response)
        val choices = json.optJSONArray("choices") ?: JSONArray()
        val first = choices.optJSONObject(0)
        val content = first?.optJSONObject("message")?.optString("content").orEmpty()
        content
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.removeSuffix(".")
            ?.take(60)
            .orEmpty()
    }

    override suspend fun generateReply(prompt: String): String = withContext(Dispatchers.IO) {
        val model = currentModel ?: return@withContext "No hay modelo seleccionado."
        val payload = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("messages", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", prompt)
            ))

        val response = request("POST", "/v1/chat/completions", payload.toString())
        val json = JSONObject(response)
        val choices = json.optJSONArray("choices") ?: JSONArray()
        if (choices.length() == 0) return@withContext "Sin respuesta del modelo."
        val first = choices.optJSONObject(0) ?: return@withContext "Sin respuesta del modelo."
        val message = first.optJSONObject("message")
        val content = message?.optString("content").orEmpty().trim()
        if (content.isNotEmpty()) content else "Sin contenido en respuesta."
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val url = URL("$baseUrl$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            doInput = true
            if (body != null) {
                doOutput = true
            }
        }

        if (body != null) {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code en $path: $responseText")
        }
        return responseText
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "http://$trimmed"
    }
}
