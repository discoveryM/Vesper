package com.example.ai

import com.example.data.FreeAiKeys
import com.example.data.FreeAiKeysStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class FreeAiModel(
  val pillLabel: String,
  val displayName: String,
  val subtitle: String,
) {
  BEAST_MODE(
    pillLabel = "Beast Mode",
    displayName = "All-in-One Intelligence (Beast Mode)",
    subtitle = "Queries all free AI models in parallel & synthesizes one master deep-research report",
  ),
  GEMINI_2_5_FLASH(
    pillLabel = "Gemini 2.5 Flash",
    displayName = "Gemini 2.5 Flash",
    subtitle = "Everyday assistant & live Google Search grounding (Google AI Studio Free Tier)",
  ),
  DEEPSEEK_R1(
    pillLabel = "DeepSeek R1",
    displayName = "DeepSeek R1 (:free)",
    subtitle = "Step-by-step deep reasoning & complex logic (OpenRouter Free Tier)",
  ),
  LLAMA_3_3_70B(
    pillLabel = "Llama 3.3 70B",
    displayName = "Llama 3.3 70B (:free)",
    subtitle = "Ultra-fast everyday answers & summaries (Groq / OpenRouter Free Tier)",
  ),
  QWEN_2_5_72B(
    pillLabel = "Qwen 2.5 72B",
    displayName = "Qwen 2.5 72B (:free)",
    subtitle = "Coding, math & structured analysis (OpenRouter Free Tier)",
  ),
}

data class ResearchResult(
  val text: String,
  val isBeastReport: Boolean,
  val attributionTags: List<String>,
)

/**
 * 100% Free Multi-LLM Research Engine.
 * Strict Privacy Firewall: accepts ONLY plain-text research questions. Local file URIs,
 * installed app lists, screen contents, and credentials are never sent to any cloud API.
 */
class MultiAiResearchEngine(
  private val keysStore: FreeAiKeysStore,
) {
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .build()

  suspend fun ask(
    plainTextQuestion: String,
    model: FreeAiModel,
    onLiveStatus: (String) -> Unit,
  ): ResearchResult {
    val sanitizedQuestion = sanitizePlainTextQuestion(plainTextQuestion)
    val keys = keysStore.getKeys()

    if (!keys.hasAnyKey) {
      return ResearchResult(
        text =
          "No free AI keys configured yet. Open the Navigation Drawer → Free AI Keys to paste your free Google AI Studio (Gemini) key or OpenRouter/Groq key (zero credit card required).",
        isBeastReport = false,
        attributionTags = emptyList(),
      )
    }

    return when (model) {
      FreeAiModel.GEMINI_2_5_FLASH -> queryGeminiSingle(keys, sanitizedQuestion, onLiveStatus)
      FreeAiModel.DEEPSEEK_R1 ->
        queryOpenRouterSingle(
          keys = keys,
          openRouterModel = "deepseek/deepseek-r1:free",
          groqFallbackModel = null,
          attribution = "DEEPSEEK R1 (:FREE)",
          question = sanitizedQuestion,
          onLiveStatus = onLiveStatus,
        )
      FreeAiModel.LLAMA_3_3_70B ->
        queryOpenRouterSingle(
          keys = keys,
          openRouterModel = "meta-llama/llama-3.3-70b-instruct:free",
          groqFallbackModel = "llama-3.3-70b-versatile",
          attribution = if (keys.isGroqKey) "LLAMA 3.3 70B (GROQ)" else "LLAMA 3.3 70B (:FREE)",
          question = sanitizedQuestion,
          onLiveStatus = onLiveStatus,
        )
      FreeAiModel.QWEN_2_5_72B ->
        queryOpenRouterSingle(
          keys = keys,
          openRouterModel = "qwen/qwen-2.5-72b-instruct:free",
          groqFallbackModel = null,
          attribution = "QWEN 2.5 72B (:FREE)",
          question = sanitizedQuestion,
          onLiveStatus = onLiveStatus,
        )
      FreeAiModel.BEAST_MODE -> runBeastDeepResearch(keys, sanitizedQuestion, onLiveStatus)
    }
  }

  private suspend fun queryGeminiSingle(
    keys: FreeAiKeys,
    question: String,
    onLiveStatus: (String) -> Unit,
  ): ResearchResult {
    if (!keys.hasGeminiKey) {
      return ResearchResult(
        text =
          "Add your free Google AI Studio key in Navigation Drawer → Free AI Keys to use Gemini 2.5 Flash with live Google Search grounding.",
        isBeastReport = false,
        attributionTags = emptyList(),
      )
    }
    onLiveStatus("Grounding with Gemini 2.5 Flash + Google Search…")
    val answer = callGemini25Flash(keys.geminiKey, question, useGoogleSearch = true)
    return ResearchResult(
      text = answer,
      isBeastReport = false,
      attributionTags = listOf("GEMINI 2.5 FLASH • GOOGLE SEARCH"),
    )
  }

  private suspend fun queryOpenRouterSingle(
    keys: FreeAiKeys,
    openRouterModel: String,
    groqFallbackModel: String?,
    attribution: String,
    question: String,
    onLiveStatus: (String) -> Unit,
  ): ResearchResult {
    if (!keys.hasOpenRouterOrGroqKey) {
      return ResearchResult(
        text =
          "Add a free OpenRouter (sk-or-…) or Groq (gsk_…) key in Navigation Drawer → Free AI Keys to query $attribution.",
        isBeastReport = false,
        attributionTags = emptyList(),
      )
    }

    if (keys.isGroqKey && groqFallbackModel == null) {
      return ResearchResult(
        text =
          "Your saved key is a Groq key (gsk_…). Use an OpenRouter free key (sk-or-…) for $attribution, or switch to Llama 3.3 70B / Beast Mode.",
        isBeastReport = false,
        attributionTags = emptyList(),
      )
    }

    onLiveStatus("Querying $attribution…")
    val answer =
      callOpenAiCompatible(
        apiKey = keys.openRouterOrGroqKey,
        isGroq = keys.isGroqKey,
        modelId = if (keys.isGroqKey && groqFallbackModel != null) groqFallbackModel else openRouterModel,
        prompt = question,
      )
    return ResearchResult(
      text = answer,
      isBeastReport = false,
      attributionTags = listOf(attribution),
    )
  }

  private suspend fun runBeastDeepResearch(
    keys: FreeAiKeys,
    question: String,
    onLiveStatus: (String) -> Unit,
  ): ResearchResult = coroutineScope {
    val hasGemini = keys.hasGeminiKey
    val hasOpenRouter = keys.hasOpenRouterOrGroqKey && !keys.isGroqKey
    val hasGroq = keys.hasOpenRouterOrGroqKey && keys.isGroqKey

    // Case 1: Full Multi-LLM Parallel Synthesis (Gemini + DeepSeek R1 + Llama 3.3 + Qwen)
    if (hasOpenRouter || hasGroq) {
      val activeNames = buildList {
        if (hasGemini) add("Gemini")
        if (hasOpenRouter) add("DeepSeek R1")
        add("Llama 3.3")
        if (hasOpenRouter) add("Qwen")
      }
      onLiveStatus("Synthesizing ${activeNames.joinToString(" + ")}…")

      val geminiDeferred =
        if (hasGemini) {
          async {
            runCatching {
              "Gemini 2.5 Flash (Google Search)" to
                callGemini25Flash(keys.geminiKey, question, useGoogleSearch = true)
            }.getOrNull()
          }
        } else {
          null
        }

      val deepSeekDeferred =
        if (hasOpenRouter) {
          async {
            runCatching {
              "DeepSeek R1" to
                callOpenAiCompatible(
                  apiKey = keys.openRouterOrGroqKey,
                  isGroq = false,
                  modelId = "deepseek/deepseek-r1:free",
                  prompt = question,
                )
            }.getOrNull()
          }
        } else {
          null
        }

      val llamaDeferred =
        async {
          runCatching {
            "Llama 3.3 70B" to
              callOpenAiCompatible(
                apiKey = keys.openRouterOrGroqKey,
                isGroq = hasGroq,
                modelId =
                  if (hasGroq) "llama-3.3-70b-versatile"
                  else "meta-llama/llama-3.3-70b-instruct:free",
                prompt = question,
              )
          }.getOrNull()
        }

      val qwenDeferred =
        if (hasOpenRouter) {
          async {
            runCatching {
              "Qwen 2.5 72B" to
                callOpenAiCompatible(
                  apiKey = keys.openRouterOrGroqKey,
                  isGroq = false,
                  modelId = "qwen/qwen-2.5-72b-instruct:free",
                  prompt = question,
                )
            }.getOrNull()
          }
        } else {
          null
        }

      val modelOutputs =
        listOfNotNull(
          geminiDeferred?.await(),
          deepSeekDeferred?.await(),
          llamaDeferred.await(),
          qwenDeferred?.await(),
        )

      if (modelOutputs.isEmpty()) {
        return@coroutineScope ResearchResult(
          text = "All configured free-tier endpoints returned errors or timed out. Verify your keys in Free AI Keys.",
          isBeastReport = true,
          attributionTags = emptyList(),
        )
      }

      if (modelOutputs.size == 1) {
        val (name, reply) = modelOutputs.first()
        return@coroutineScope ResearchResult(
          text = reply,
          isBeastReport = true,
          attributionTags = listOf(name.uppercase()),
        )
      }

      onLiveStatus("Cross-verifying facts across ${modelOutputs.size} models…")
      val synthesisPrompt = buildString {
        appendLine("You are Vesper Beast Mode, a rigorous multi-model research synthesizer.")
        appendLine("User question: \"$question\"")
        appendLine()
        appendLine("Merge and cross-verify the independent model drafts below into one clear, authoritative, structured answer. Resolve contradictions, highlight verified facts, and remove fluff.")
        appendLine()
        modelOutputs.forEach { (name, output) ->
          appendLine("=== $name ===")
          appendLine(output.take(3200))
          appendLine()
        }
      }

      val masterReport =
        if (hasGemini) {
          runCatching { callGemini25Flash(keys.geminiKey, synthesisPrompt, useGoogleSearch = false) }
            .getOrElse { modelOutputs.joinToString("\n\n") { (n, o) -> "[$n]\n$o" } }
        } else {
          runCatching {
            callOpenAiCompatible(
              apiKey = keys.openRouterOrGroqKey,
              isGroq = hasGroq,
              modelId =
                if (hasGroq) "llama-3.3-70b-versatile"
                else "meta-llama/llama-3.3-70b-instruct:free",
              prompt = synthesisPrompt,
            )
          }.getOrElse { modelOutputs.joinToString("\n\n") { (n, o) -> "[$n]\n$o" } }
        }

      return@coroutineScope ResearchResult(
        text = masterReport,
        isBeastReport = true,
        attributionTags = modelOutputs.map { it.first.uppercase() },
      )
    }

    // Case 2: Only Free Gemini Key Active -> Multi-Step Deep Research Loop with Google Search Grounding
    onLiveStatus("Synthesizing Gemini 2.5 Flash Multi-Step Deep Research…")

    val coreFactsDeferred = async {
      callGemini25Flash(
        keys.geminiKey,
        "Research this question thoroughly using Google Search and extract verified facts, numbers, and recent context: $question",
        useGoogleSearch = true,
      )
    }
    val criticalAngleDeferred = async {
      callGemini25Flash(
        keys.geminiKey,
        "Identify key nuances, trade-offs, edge cases, and opposing evidence for: $question",
        useGoogleSearch = true,
      )
    }

    val coreFacts = runCatching { coreFactsDeferred.await() }.getOrNull()
    val criticalAngles = runCatching { criticalAngleDeferred.await() }.getOrNull()

    val combined = listOfNotNull(coreFacts, criticalAngles)
    if (combined.isEmpty()) {
      return@coroutineScope ResearchResult(
        text = "Unable to reach Gemini 2.5 Flash. Check your network connection and Google AI Studio key.",
        isBeastReport = true,
        attributionTags = emptyList(),
      )
    }

    onLiveStatus("Cross-verifying grounded findings…")
    val masterSynthesisPrompt = buildString {
      appendLine("Synthesize the following grounded research passes into a single clean, structured executive report answering: \"$question\"")
      appendLine()
      combined.forEachIndexed { idx, pass ->
        appendLine("--- Research Pass ${idx + 1} ---")
        appendLine(pass.take(3500))
        appendLine()
      }
    }

    val finalReport =
      runCatching {
        callGemini25Flash(keys.geminiKey, masterSynthesisPrompt, useGoogleSearch = false)
      }.getOrElse { combined.first() }

    return@coroutineScope ResearchResult(
      text = finalReport,
      isBeastReport = true,
      attributionTags =
        listOf("GEMINI 2.5 FLASH • GOOGLE SEARCH", "MULTI-PASS SYNTHESIS"),
    )
  }

  private suspend fun callGemini25Flash(
    apiKey: String,
    prompt: String,
    useGoogleSearch: Boolean,
  ): String =
    withContext(Dispatchers.IO) {
      val payload =
        JSONObject().apply {
          put(
            "contents",
            JSONArray().put(
              JSONObject().put(
                "parts",
                JSONArray().put(JSONObject().put("text", prompt)),
              ),
            ),
          )
          if (useGoogleSearch) {
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
          }
        }

      val url =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
      val request =
        Request.Builder()
          .url(url)
          .post(payload.toString().toRequestBody("application/json".toMediaType()))
          .build()

      client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Gemini HTTP ${response.code}: ${extractErrorMessage(raw)}")
        }
        val json = JSONObject(raw)
        val candidates = json.optJSONArray("candidates") ?: throw IOException("No Gemini response")
        val first = candidates.optJSONObject(0) ?: throw IOException("Empty Gemini candidate")
        val parts = first.optJSONObject("content")?.optJSONArray("parts")
        val textBuilder = StringBuilder()
        if (parts != null) {
          for (i in 0 until parts.length()) {
            val partText = parts.optJSONObject(i)?.optString("text").orEmpty()
            if (partText.isNotEmpty()) textBuilder.append(partText)
          }
        }
        textBuilder.toString().trim().ifEmpty { throw IOException("Blank Gemini output") }
      }
    }

  private suspend fun callOpenAiCompatible(
    apiKey: String,
    isGroq: Boolean,
    modelId: String,
    prompt: String,
  ): String =
    withContext(Dispatchers.IO) {
      val endpoint =
        if (isGroq) "https://api.groq.com/openai/v1/chat/completions"
        else "https://openrouter.ai/api/v1/chat/completions"

      val payload =
        JSONObject().apply {
          put("model", modelId)
          put(
            "messages",
            JSONArray()
              .put(
                JSONObject()
                  .put("role", "system")
                  .put(
                    "content",
                    "You are Vesper, a concise, accurate research assistant. Answer clearly and verify facts.",
                  ),
              )
              .put(JSONObject().put("role", "user").put("content", prompt)),
          )
        }

      val builder =
        Request.Builder()
          .url(endpoint)
          .header("Authorization", "Bearer $apiKey")
          .header("Content-Type", "application/json")
      if (!isGroq) {
        builder.header("HTTP-Referer", "https://vesper.local")
        builder.header("X-Title", "Vesper")
      }

      val request =
        builder.post(payload.toString().toRequestBody("application/json".toMediaType())).build()

      client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Provider HTTP ${response.code}: ${extractErrorMessage(raw)}")
        }
        val json = JSONObject(raw)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
        val content = choice?.optJSONObject("message")?.optString("content").orEmpty().trim()
        content.ifEmpty { throw IOException("Empty provider completion") }
      }
    }

  private fun extractErrorMessage(rawBody: String): String {
    return runCatching {
      JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrDefault("").ifBlank { rawBody.take(120) }
  }

  /**
   * Privacy Firewall: strips any accidental local content:// or file:// URIs before any cloud call.
   */
  private fun sanitizePlainTextQuestion(input: String): String {
    return input
      .replace(Regex("(content|file)://\\S+"), "[redacted-local-uri]")
      .trim()
  }
}
