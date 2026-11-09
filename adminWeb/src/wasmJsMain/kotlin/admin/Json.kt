package admin

// Minimal JSON reader over the browser's own JSON.parse (no serialization plugin needed). Callable results arrive as JSON text.
private fun parseJs(text: String): JsAny? = js("JSON.parse(text)")
private fun getJs(o: JsAny, k: String): JsAny? = js("(o[k] === undefined ? null : o[k])")
private fun kindJs(o: JsAny): String = js("(Array.isArray(o) ? 'array' : typeof o)")
private fun strJs(o: JsAny): String = js("String(o)")
private fun numJs(o: JsAny): Double = js("Number(o)")
private fun lenJs(o: JsAny): Int = js("o.length")
private fun atJs(o: JsAny, i: Int): JsAny? = js("(o[i] === undefined ? null : o[i])")

/** Escapes [s] as a JSON string literal (quotes included). */
fun jsonQuote(s: String): String = quoteJs(s)
private fun quoteJs(s: String): String = js("JSON.stringify(s)")

/** `Mon d, yyyy` in English for epoch millis (the Android console prints the same pattern). */
fun dateLabel(millis: Double): String = dateJs(millis)
private fun dateJs(ms: Double): String = js("new Date(ms).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })")

class Json private constructor(private val v: JsAny?) {
  operator fun get(key: String): Json = Json(v?.takeIf { kindJs(it) == "object" }?.let { getJs(it, key) })
  val isNull: Boolean get() = v == null
  fun str(default: String = ""): String = v?.takeIf { kindJs(it) == "string" || kindJs(it) == "number" }?.let { strJs(it) } ?: default
  fun num(): Double? = v?.takeIf { kindJs(it) == "number" }?.let { numJs(it) }
  fun int(default: Int = 0): Int = num()?.toInt() ?: default
  fun bool(default: Boolean = false): Boolean = v?.takeIf { kindJs(it) == "boolean" }?.let { strJs(it) == "true" } ?: default
  fun list(): List<Json> {
    val a = v?.takeIf { kindJs(it) == "array" } ?: return emptyList()
    return List(lenJs(a)) { Json(atJs(a, it)) }
  }

  companion object {
    fun parse(text: String): Json = Json(parseJs(text))
  }
}
