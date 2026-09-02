package com.pinealctx.nexus.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pinealctx.nexus.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private const val SUPPORTED_CARD_MAJOR = 1
private const val SUPPORTED_CARD_MINOR = 5
private val LocalCardMediaUrls = compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
fun AdaptiveCardView(
    json: String,
    fallbackText: String = "",
    modifier: Modifier = Modifier,
    mediaController: ChatMediaController? = null,
    resolvedMediaUrls: Map<String, String> = emptyMap(),
    onMediaNeeded: (String) -> Unit = {},
    onAction: (String, String) -> Unit = { _, _ -> },
    onOpenMiniApp: ((Int, String) -> Unit)? = null
) {
    val invalidCardText = stringResource(R.string.card_invalid)
    val parsed = remember(json) { parseCard(json) }
    val card = parsed.card
    val effectiveFallback = fallbackText.ifBlank { card?.optString("fallbackText").orEmpty() }

    if (card == null || !isSupportedCard(card)) {
        Text(
            text = effectiveFallback.ifBlank { invalidCardText },
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        return
    }

    val state = remember(json) { CardRenderState(card) }
    val referencedFileIds = remember(json) { findNexusFileIds(card) }
    LaunchedEffect(referencedFileIds) {
        referencedFileIds.forEach(onMediaNeeded)
    }
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = { url ->
        if (isAllowedCardActionUrl(url)) runCatching { uriHandler.openUri(url) }
    }

    CompositionLocalProvider(LocalCardMediaUrls provides resolvedMediaUrls) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            CardContainer(
                element = card,
                state = state,
                mediaController = mediaController,
                onAction = onAction,
                onOpenUrl = openUrl,
                onOpenMiniApp = onOpenMiniApp,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

private data class ParsedCard(val card: JSONObject?)

private fun parseCard(json: String): ParsedCard = ParsedCard(
    card = runCatching { JSONObject(json) }
        .getOrNull()
        ?.takeIf { it.optString("type") == "AdaptiveCard" }
)

private fun isSupportedCard(card: JSONObject): Boolean {
    val version = card.optString("version", "1.0").split('.')
    val major = version.getOrNull(0)?.toIntOrNull() ?: return false
    val minor = version.getOrNull(1)?.toIntOrNull() ?: 0
    return major < SUPPORTED_CARD_MAJOR ||
        (major == SUPPORTED_CARD_MAJOR && minor <= SUPPORTED_CARD_MINOR)
}

@Stable
private class CardRenderState(root: JSONObject) {
    val values = mutableStateMapOf<String, String>()
    val errors = mutableStateMapOf<String, String>()
    private val inputSpecs = linkedMapOf<String, JSONObject>()
    private val visibility = mutableStateMapOf<String, Boolean>()
    private val showCards = mutableStateMapOf<String, Boolean>()

    init {
        scanCard(root)
    }

    fun key(element: JSONObject): String = element.optString("id").takeIf { it.isNotBlank() }
        ?: "anonymous-${System.identityHashCode(element)}"

    fun isVisible(element: JSONObject): Boolean = visibility[key(element)]
        ?: element.optBoolean("isVisible", true)

    fun isShowCardVisible(action: JSONObject): Boolean = showCards[key(action)] == true

    fun toggleShowCard(action: JSONObject) {
        val key = key(action)
        showCards[key] = showCards[key] != true
    }

    fun toggleVisibility(targets: JSONArray) {
        for (index in 0 until targets.length()) {
            when (val target = targets.opt(index)) {
                is String -> visibility[target] = visibility[target] != true
                is JSONObject -> {
                    val id = target.optString("elementId")
                    if (id.isBlank()) continue
                    visibility[id] = if (target.has("isVisible")) {
                        target.optBoolean("isVisible")
                    } else {
                        visibility[id] != true
                    }
                }
            }
        }
    }

    fun buildSubmitPayload(action: JSONObject, includeInputs: Boolean): JSONObject? {
        if (includeInputs && !validateInputs()) return null
        val data = action.opt("data")
        val payload = if (data is JSONObject) {
            JSONObject(data.toString())
        } else if (data is String) {
            runCatching { JSONObject(data) }.getOrElse {
                JSONObject().put("data", data)
            }
        } else if (data == null || data == JSONObject.NULL) {
            JSONObject()
        } else {
            JSONObject().put("data", data)
        }
        if (includeInputs) {
            inputSpecs.forEach { (key, spec) ->
                val id = spec.optString("id")
                if (id.isNotBlank()) payload.put(id, values[key].orEmpty())
            }
        }
        return payload
    }

    private fun validateInputs(): Boolean {
        errors.clear()
        inputSpecs.forEach { (key, spec) ->
            val value = values[key].orEmpty()
            val error = when {
                spec.optBoolean("isRequired") && value.isBlank() -> requiredMessage(spec)
                spec.optString("type") == "Input.Text" &&
                    spec.optInt("maxLength", 0) > 0 && value.length > spec.optInt("maxLength") ->
                    spec.optString("errorMessage").ifBlank { "Maximum length exceeded" }
                spec.optString("type") == "Input.Text" && spec.optString("regex").isNotBlank() &&
                    !runCatching { Regex(spec.optString("regex")).matches(value) }.getOrDefault(false) ->
                    spec.optString("errorMessage").ifBlank { "Invalid value" }
                spec.optString("type") == "Input.Number" && value.isNotBlank() -> validateNumber(spec, value)
                else -> null
            }
            if (error != null) errors[key] = error
        }
        return errors.isEmpty()
    }

    private fun validateNumber(spec: JSONObject, value: String): String? {
        val number = value.toDoubleOrNull()
            ?: return spec.optString("errorMessage").ifBlank { "Enter a valid number" }
        val belowMin = spec.has("min") && number < spec.optDouble("min")
        val aboveMax = spec.has("max") && number > spec.optDouble("max")
        return if (belowMin || aboveMax) {
            spec.optString("errorMessage").ifBlank { "Number is outside the allowed range" }
        } else null
    }

    private fun requiredMessage(spec: JSONObject): String =
        spec.optString("errorMessage").ifBlank { "This field is required" }

    private fun scanCard(card: JSONObject) {
        scanElements(card.optJSONArray("body"))
        scanActions(card.optJSONArray("actions"))
    }

    private fun scanElements(elements: JSONArray?) {
        if (elements == null) return
        for (index in 0 until elements.length()) {
            val element = elements.optJSONObject(index) ?: continue
            element.optString("id").takeIf { it.isNotBlank() }?.let {
                visibility[it] = element.optBoolean("isVisible", true)
            }
            if (element.optString("type").startsWith("Input.")) registerInput(element)
            scanElements(element.optJSONArray("items"))
            scanElements(element.optJSONArray("columns"))
            scanElements(element.optJSONArray("images"))
            scanActions(element.optJSONArray("actions"))
            val rows = element.optJSONArray("rows")
            if (rows != null) {
                for (rowIndex in 0 until rows.length()) {
                    val cells = rows.optJSONObject(rowIndex)?.optJSONArray("cells") ?: continue
                    for (cellIndex in 0 until cells.length()) {
                        scanElements(cells.optJSONObject(cellIndex)?.optJSONArray("items"))
                    }
                }
            }
        }
    }

    private fun scanActions(actions: JSONArray?) {
        if (actions == null) return
        for (index in 0 until actions.length()) {
            val action = actions.optJSONObject(index) ?: continue
            action.optJSONObject("card")?.let(::scanCard)
        }
    }

    private fun registerInput(input: JSONObject) {
        val key = key(input)
        inputSpecs[key] = input
        values[key] = when (input.optString("type")) {
            "Input.Toggle" -> input.optString("value").ifBlank { input.optString("valueOff", "false") }
            else -> input.optString("value")
        }
    }
}

@Composable
private fun CardContainer(
    element: JSONObject,
    state: CardRenderState,
    mediaController: ChatMediaController?,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val background = element.opt("backgroundImage")
    val backgroundReference = if (background is String) {
        background
    } else if (background is JSONObject) {
        background.optString("url")
    } else {
        ""
    }
    val backgroundUrl = resolveCardMediaUrl(backgroundReference)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = parseCardDimension(element.optString("minHeight")))
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor(element.optString("style")))
            .clickable(
                enabled = element.optJSONObject("selectAction") != null,
                onClick = {
                    element.optJSONObject("selectAction")?.let {
                        executeLocalAction(it, state, onAction, onOpenUrl, onOpenMiniApp)
                    }
                }
            )
    ) {
        if (backgroundUrl != null) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(
            verticalArrangement = when (element.optString("verticalContentAlignment")) {
                "center" -> Arrangement.Center
                "bottom" -> Arrangement.Bottom
                else -> Arrangement.Top
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            RenderElements(
                element.optJSONArray("body") ?: element.optJSONArray("items"),
                state,
                mediaController,
                onAction,
                onOpenUrl,
                onOpenMiniApp
            )
            val actions = element.optJSONArray("actions")
            if (actions != null && actions.length() > 0) {
                Spacer(Modifier.height(12.dp))
                RenderActions(actions, state, mediaController, onAction, onOpenUrl, onOpenMiniApp)
            }
        }
    }
}

@Composable
private fun RenderElements(
    elements: JSONArray?,
    state: CardRenderState,
    mediaController: ChatMediaController?,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    if (elements == null) return
    for (index in 0 until elements.length()) {
        val element = elements.optJSONObject(index) ?: continue
        if (!state.isVisible(element)) continue
        val spacing = cardSpacing(element.optString("spacing", if (index == 0) "none" else "default"))
        Column(modifier = Modifier.fillMaxWidth().padding(top = spacing)) {
            if (element.optBoolean("separator")) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(8.dp))
            }
            RenderElement(element, state, mediaController, onAction, onOpenUrl, onOpenMiniApp)
        }
    }
}

@Composable
private fun RenderElement(
    element: JSONObject,
    state: CardRenderState,
    mediaController: ChatMediaController?,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    when (element.optString("type")) {
        "TextBlock" -> RenderTextBlock(element)
        "RichTextBlock" -> RenderRichTextBlock(element, state, onAction, onOpenUrl, onOpenMiniApp)
        "Image" -> RenderImage(element, state, onAction, onOpenUrl, onOpenMiniApp)
        "ImageSet" -> RenderImageSet(element, state, onAction, onOpenUrl, onOpenMiniApp)
        "Media" -> RenderMedia(element, mediaController)
        "ColumnSet" -> RenderColumnSet(element, state, mediaController, onAction, onOpenUrl, onOpenMiniApp)
        "Column", "Container" -> CardContainer(
            element,
            state,
            mediaController,
            onAction,
            onOpenUrl,
            onOpenMiniApp,
            Modifier.padding(if (element.optString("style").isBlank()) 0.dp else 8.dp)
        )
        "ActionSet" -> RenderActions(
            element.optJSONArray("actions") ?: JSONArray(),
            state,
            mediaController,
            onAction,
            onOpenUrl,
            onOpenMiniApp
        )
        "FactSet" -> RenderFactSet(element)
        "Table" -> RenderTable(element, state, mediaController, onAction, onOpenUrl, onOpenMiniApp)
        "Input.Text" -> RenderTextInput(element, state, onAction, onOpenUrl, onOpenMiniApp)
        "Input.Number" -> RenderNumberInput(element, state)
        "Input.Date" -> RenderDateInput(element, state)
        "Input.Time" -> RenderTimeInput(element, state)
        "Input.Toggle" -> RenderToggleInput(element, state)
        "Input.ChoiceSet" -> RenderChoiceInput(element, state)
        else -> RenderElementFallback(element, state, mediaController, onAction, onOpenUrl, onOpenMiniApp)
    }
}

@Composable
private fun RenderTextBlock(element: JSONObject) {
    val text = element.optString("text")
    if (text.containsCardMarkdown()) {
        MarkdownText(text = text, modifier = Modifier.fillMaxWidth())
        return
    }
    val style = when (element.optString("size")) {
        "extraLarge" -> MaterialTheme.typography.headlineSmall
        "large" -> MaterialTheme.typography.titleLarge
        "medium" -> MaterialTheme.typography.titleMedium
        "small" -> MaterialTheme.typography.bodySmall
        else -> MaterialTheme.typography.bodyMedium
    }.copy(fontFamily = if (element.optString("fontType") == "monospace") FontFamily.Monospace else null)
    Text(
        text = text,
        style = style,
        color = cardTextColor(element.optString("color"), element.optBoolean("isSubtle")),
        fontWeight = when (element.optString("weight")) {
            "bolder" -> FontWeight.Bold
            "lighter" -> FontWeight.Light
            else -> FontWeight.Normal
        },
        maxLines = element.optInt("maxLines", if (element.optBoolean("wrap", true)) Int.MAX_VALUE else 1),
        textAlign = cardTextAlign(element.optString("horizontalAlignment")),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RenderRichTextBlock(
    element: JSONObject,
    state: CardRenderState,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    val inlines = element.optJSONArray("inlines") ?: return
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        for (index in 0 until inlines.length()) {
            val run = inlines.optJSONObject(index) ?: continue
            val action = run.optJSONObject("selectAction")
            Text(
                text = run.optString("text"),
                color = cardTextColor(run.optString("color"), run.optBoolean("isSubtle")),
                fontSize = cardFontSize(run.optString("size")),
                fontWeight = if (run.optString("weight") == "bolder") FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (run.optBoolean("italic")) FontStyle.Italic else FontStyle.Normal,
                fontFamily = if (run.optString("fontType") == "monospace") FontFamily.Monospace else null,
                textDecoration = when {
                    run.optBoolean("strikethrough") -> TextDecoration.LineThrough
                    run.optBoolean("underline") || action != null -> TextDecoration.Underline
                    else -> null
                },
                modifier = Modifier
                    .background(
                        if (run.optBoolean("highlight")) MaterialTheme.colorScheme.tertiaryContainer
                        else Color.Transparent
                    )
                    .clickable(enabled = action != null) {
                        action?.let { executeLocalAction(it, state, onAction, onOpenUrl, onOpenMiniApp) }
                    }
            )
        }
    }
}

@Composable
private fun RenderImage(
    element: JSONObject,
    state: CardRenderState,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    val url = resolveCardMediaUrl(element.optString("url")) ?: return
    val action = element.optJSONObject("selectAction")
    val modifier = when (element.optString("size")) {
        "small" -> Modifier.size(48.dp)
        "medium" -> Modifier.size(88.dp)
        "large" -> Modifier.size(160.dp)
        "stretch" -> Modifier.fillMaxWidth().heightIn(max = 280.dp)
        else -> Modifier.widthIn(max = 280.dp).heightIn(max = 240.dp)
    }
    AsyncImage(
        model = url,
        contentDescription = element.optString("altText").ifBlank { null },
        contentScale = if (element.optString("size") == "stretch") ContentScale.FillWidth else ContentScale.Fit,
        modifier = modifier
            .clip(if (element.optString("style") == "person") CircleShape else RoundedCornerShape(8.dp))
            .clickable(enabled = action != null) {
                action?.let { executeLocalAction(it, state, onAction, onOpenUrl, onOpenMiniApp) }
            }
    )
}

@Composable
private fun RenderImageSet(
    element: JSONObject,
    state: CardRenderState,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    val images = element.optJSONArray("images") ?: return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (index in 0 until images.length()) {
            val image = images.optJSONObject(index) ?: continue
            if (!image.has("size") && element.optString("imageSize").isNotBlank()) {
                image.put("size", element.optString("imageSize"))
            }
            RenderImage(image, state, onAction, onOpenUrl, onOpenMiniApp)
        }
    }
}

@Composable
private fun RenderMedia(element: JSONObject, mediaController: ChatMediaController?) {
    val sources = element.optJSONArray("sources") ?: return
    var source: JSONObject? = null
    var url: String? = null
    for (index in 0 until sources.length()) {
        val candidate = sources.optJSONObject(index) ?: continue
        val resolved = resolveCardMediaUrl(candidate.optString("url")) ?: continue
        source = candidate
        url = resolved
        break
    }
    val selectedSource = source ?: return
    val selectedUrl = url ?: return
    val mimeType = selectedSource.optString("mimeType")
    if (mimeType.startsWith("audio/")) {
        AudioMessagePlayer(
            mediaId = "card:$selectedUrl",
            url = selectedUrl,
            declaredDurationMs = 0,
            controller = mediaController
        )
    } else {
        VideoMessagePlayer(
            mediaId = "card:$selectedUrl",
            url = selectedUrl,
            thumbnailUrl = resolveCardMediaUrl(element.optString("poster")),
            declaredDurationMs = 0,
            width = 16,
            height = 9,
            controller = mediaController
        )
    }
}

@Composable
private fun RenderColumnSet(
    element: JSONObject,
    state: CardRenderState,
    mediaController: ChatMediaController?,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    val columns = element.optJSONArray("columns") ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        for (index in 0 until columns.length()) {
            val column = columns.optJSONObject(index) ?: continue
            if (!state.isVisible(column)) continue
            val width = column.optString("width", "stretch")
            val modifier = when (width) {
                "auto" -> Modifier.width(IntrinsicSize.Min)
                "stretch" -> Modifier.widthIn(min = 120.dp, max = 280.dp)
                else -> Modifier.widthIn(min = (width.removeSuffix("px").toIntOrNull() ?: 120).dp)
            }
            CardContainer(
                column,
                state,
                mediaController,
                onAction,
                onOpenUrl,
                onOpenMiniApp,
                modifier
            )
        }
    }
}

@Composable
private fun RenderFactSet(element: JSONObject) {
    val facts = element.optJSONArray("facts") ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in 0 until facts.length()) {
            val fact = facts.optJSONObject(index) ?: continue
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fact.optString("title"),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.4f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = fact.optString("value"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
    }
}

@Composable
private fun RenderTable(
    element: JSONObject,
    state: CardRenderState,
    mediaController: ChatMediaController?,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    val rows = element.optJSONArray("rows") ?: return
    val columnCount = (0 until rows.length()).maxOfOrNull {
        rows.optJSONObject(it)?.optJSONArray("cells")?.length() ?: 0
    } ?: 0
    if (columnCount == 0) return
    Column(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        for (rowIndex in 0 until rows.length()) {
            val row = rows.optJSONObject(rowIndex) ?: continue
            val cells = row.optJSONArray("cells") ?: continue
            Row(modifier = Modifier.width(IntrinsicSize.Max)) {
                for (cellIndex in 0 until columnCount) {
                    val cell = cells.optJSONObject(cellIndex)
                    Surface(
                        color = if (rowIndex == 0 && element.optBoolean("firstRowAsHeader", true)) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else Color.Transparent,
                        border = if (element.optBoolean("showGridLines", true)) {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        } else null,
                        modifier = Modifier.widthIn(min = 112.dp, max = 220.dp).height(IntrinsicSize.Min)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            RenderElements(
                                cell?.optJSONArray("items"),
                                state,
                                mediaController,
                                onAction,
                                onOpenUrl,
                                onOpenMiniApp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputLabel(element: JSONObject) {
    element.optString("label").takeIf { it.isNotBlank() }?.let { label ->
        Text(
            text = if (element.optBoolean("isRequired")) "$label *" else label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun InputError(element: JSONObject, state: CardRenderState) {
    state.errors[state.key(element)]?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RenderTextInput(
    element: JSONObject,
    state: CardRenderState,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    val key = state.key(element)
    val password = element.optString("style") == "password"
    InputLabel(element)
    OutlinedTextField(
        value = state.values[key].orEmpty(),
        onValueChange = { state.values[key] = it },
        placeholder = { Text(element.optString("placeholder")) },
        minLines = if (element.optBoolean("isMultiline")) 3 else 1,
        maxLines = if (element.optBoolean("isMultiline")) 6 else 1,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (element.optString("style")) {
                "tel" -> KeyboardType.Phone
                "url" -> KeyboardType.Uri
                "email" -> KeyboardType.Email
                "password" -> KeyboardType.Password
                else -> KeyboardType.Text
            }
        ),
        trailingIcon = element.optJSONObject("inlineAction")?.let { action ->
            {
                IconButton(onClick = {
                    executeLocalAction(action, state, onAction, onOpenUrl, onOpenMiniApp)
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = action.optString("title"))
                }
            }
        },
        isError = state.errors.containsKey(key),
        modifier = Modifier.fillMaxWidth()
    )
    InputError(element, state)
}

@Composable
private fun RenderNumberInput(element: JSONObject, state: CardRenderState) {
    val key = state.key(element)
    InputLabel(element)
    OutlinedTextField(
        value = state.values[key].orEmpty(),
        onValueChange = { state.values[key] = it },
        placeholder = { Text(element.optString("placeholder")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = state.errors.containsKey(key),
        modifier = Modifier.fillMaxWidth()
    )
    InputError(element, state)
}

@Composable
private fun RenderDateInput(element: JSONObject, state: CardRenderState) {
    val context = LocalContext.current
    val key = state.key(element)
    InputLabel(element)
    OutlinedTextField(
        value = state.values[key].orEmpty(),
        onValueChange = {},
        readOnly = true,
        placeholder = { Text(element.optString("placeholder")) },
        trailingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = null) },
        isError = state.errors.containsKey(key),
        modifier = Modifier.fillMaxWidth().clickable {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day -> state.values[key] = "%04d-%02d-%02d".format(year, month + 1, day) },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    )
    InputError(element, state)
}

@Composable
private fun RenderTimeInput(element: JSONObject, state: CardRenderState) {
    val context = LocalContext.current
    val key = state.key(element)
    InputLabel(element)
    OutlinedTextField(
        value = state.values[key].orEmpty(),
        onValueChange = {},
        readOnly = true,
        placeholder = { Text(element.optString("placeholder")) },
        trailingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = null) },
        isError = state.errors.containsKey(key),
        modifier = Modifier.fillMaxWidth().clickable {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                context,
                { _, hour, minute -> state.values[key] = "%02d:%02d".format(hour, minute) },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
    )
    InputError(element, state)
}

@Composable
private fun RenderToggleInput(element: JSONObject, state: CardRenderState) {
    val key = state.key(element)
    val onValue = element.optString("valueOn", "true")
    val offValue = element.optString("valueOff", "false")
    InputLabel(element)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable {
            state.values[key] = if (state.values[key] == onValue) offValue else onValue
        }
    ) {
        Switch(
            checked = state.values[key] == onValue,
            onCheckedChange = { state.values[key] = if (it) onValue else offValue }
        )
        Spacer(Modifier.width(10.dp))
        Text(element.optString("title"), style = MaterialTheme.typography.bodyMedium)
    }
    InputError(element, state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderChoiceInput(element: JSONObject, state: CardRenderState) {
    val key = state.key(element)
    val choices = element.optJSONArray("choices") ?: JSONArray()
    val multi = element.optBoolean("isMultiSelect")
    val expandedStyle = element.optString("style") == "expanded" || multi
    InputLabel(element)
    if (expandedStyle) {
        val selected = state.values[key].orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        Column {
            for (index in 0 until choices.length()) {
                val choice = choices.optJSONObject(index) ?: continue
                val value = choice.optString("value")
                val checked = value in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        state.values[key] = if (multi) {
                            (if (checked) selected - value else selected + value).joinToString(",")
                        } else value
                    }
                ) {
                    if (multi) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                state.values[key] = (if (it) selected + value else selected - value).joinToString(",")
                            }
                        )
                    } else {
                        RadioButton(selected = checked, onClick = { state.values[key] = value })
                    }
                    Text(choice.optString("title"), modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }
        val selectedTitle = (0 until choices.length())
            .mapNotNull { choices.optJSONObject(it) }
            .firstOrNull { it.optString("value") == state.values[key] }
            ?.optString("title")
            .orEmpty()
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedTitle,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(element.optString("placeholder")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                isError = state.errors.containsKey(key),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (index in 0 until choices.length()) {
                    val choice = choices.optJSONObject(index) ?: continue
                    DropdownMenuItem(
                        text = { Text(choice.optString("title")) },
                        trailingIcon = if (choice.optString("value") == state.values[key]) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            state.values[key] = choice.optString("value")
                            expanded = false
                        }
                    )
                }
            }
        }
    }
    InputError(element, state)
}

@Composable
private fun RenderActions(
    actions: JSONArray,
    state: CardRenderState,
    mediaController: ChatMediaController?,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (index in 0 until actions.length()) {
            val action = actions.optJSONObject(index) ?: continue
            RenderActionButton(action, state, onAction, onOpenUrl, onOpenMiniApp)
            if (action.optString("type") == "Action.ShowCard" && state.isShowCardVisible(action)) {
                action.optJSONObject("card")?.let { card ->
                    CardContainer(
                        card,
                        state,
                        mediaController,
                        onAction,
                        onOpenUrl,
                        onOpenMiniApp,
                        Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderActionButton(
    action: JSONObject,
    state: CardRenderState,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    val title = action.optString("title", "Action")
    val enabled = action.optBoolean("isEnabled", true)
    val click = { executeLocalAction(action, state, onAction, onOpenUrl, onOpenMiniApp) }
    when (action.optString("style")) {
        "destructive" -> OutlinedButton(
            onClick = click,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) { Text(title) }
        "positive" -> Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(title)
        }
        else -> if (action.optString("mode") == "secondary") {
            TextButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(title) }
        } else {
            OutlinedButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(title) }
        }
    }
}

private fun executeLocalAction(
    action: JSONObject,
    state: CardRenderState,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    when (action.optString("type")) {
        "Action.OpenUrl" -> onOpenUrl(action.optString("url"))
        "Action.ShowCard" -> state.toggleShowCard(action)
        "Action.ToggleVisibility" -> state.toggleVisibility(action.optJSONArray("targetElements") ?: JSONArray())
        "Action.OpenMiniApp" -> {
            val data = action.optJSONObject("data")
            val agentUserId = action.optInt("agent_user_id")
                .takeIf { it > 0 }
                ?: data?.optInt("agent_user_id")?.takeIf { it > 0 }
                ?: 0
            val startParam = action.optString("start_param")
                .ifBlank { data?.optString("start_param").orEmpty() }
            onOpenMiniApp?.invoke(agentUserId, startParam)
        }
        "Action.Submit", "Action.Execute" -> {
            val includeInputs = action.optString("associatedInputs", "auto") != "none"
            val payload = state.buildSubmitPayload(action, includeInputs) ?: return
            val verb = action.optString("verb")
                .ifBlank { action.optString("id") }
                .ifBlank { payload.optString("verb") }
            if (verb == "open_mini_app" && onOpenMiniApp != null) {
                onOpenMiniApp(payload.optInt("agent_user_id"), payload.optString("start_param"))
            } else {
                onAction(verb, payload.toString())
            }
        }
    }
}

@Composable
private fun RenderElementFallback(
    element: JSONObject,
    state: CardRenderState,
    mediaController: ChatMediaController?,
    onAction: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMiniApp: ((Int, String) -> Unit)?
) {
    when (val fallback = element.opt("fallback")) {
        is JSONObject -> RenderElement(fallback, state, mediaController, onAction, onOpenUrl, onOpenMiniApp)
        is String -> if (fallback != "drop") {
            Text(fallback, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun containerColor(style: String): Color = when (style) {
    "emphasis" -> MaterialTheme.colorScheme.surfaceVariant
    "good" -> MaterialTheme.colorScheme.primaryContainer
    "attention" -> MaterialTheme.colorScheme.errorContainer
    "warning" -> MaterialTheme.colorScheme.tertiaryContainer
    "accent" -> MaterialTheme.colorScheme.secondaryContainer
    else -> Color.Transparent
}

@Composable
private fun cardTextColor(value: String, subtle: Boolean): Color {
    val base = when (value) {
        "accent" -> MaterialTheme.colorScheme.primary
        "good" -> MaterialTheme.colorScheme.primary
        "warning" -> MaterialTheme.colorScheme.tertiary
        "attention" -> MaterialTheme.colorScheme.error
        "light" -> Color.White
        "dark" -> Color.Black
        else -> MaterialTheme.colorScheme.onSurface
    }
    return if (subtle) base.copy(alpha = 0.68f) else base
}

private fun cardFontSize(size: String) = when (size) {
    "small" -> 12.sp
    "medium" -> 16.sp
    "large" -> 20.sp
    "extraLarge" -> 24.sp
    else -> 14.sp
}

private fun cardTextAlign(alignment: String): TextAlign = when (alignment) {
    "center" -> TextAlign.Center
    "right" -> TextAlign.End
    else -> TextAlign.Start
}

private fun cardSpacing(spacing: String): Dp = when (spacing) {
    "none" -> 0.dp
    "small" -> 4.dp
    "medium" -> 12.dp
    "large" -> 18.dp
    "extraLarge" -> 28.dp
    "padding" -> 14.dp
    else -> 8.dp
}

private fun parseCardDimension(value: String): Dp =
    (value.removeSuffix("px").toFloatOrNull() ?: 0f).dp

private fun isAllowedCardActionUrl(value: String): Boolean {
    if (value.isBlank()) return false
    val scheme = runCatching { Uri.parse(value).scheme?.lowercase() }.getOrNull()
    return scheme in setOf("https", "http", "mailto", "tel", "nexus")
}

@Composable
private fun resolveCardMediaUrl(reference: String): String? {
    if (reference.startsWith("nexus-file://")) {
        val fileId = reference.removePrefix("nexus-file://").substringBefore('/').substringBefore('?')
        return LocalCardMediaUrls.current[fileId]
    }
    if (reference.startsWith("data:image/")) return reference
    return reference.takeIf {
        runCatching { Uri.parse(it).scheme?.lowercase() == "https" }.getOrDefault(false)
    }
}

private fun findNexusFileIds(root: JSONObject): Set<String> {
    val ids = linkedSetOf<String>()
    fun visit(value: Any?) {
        when (value) {
            is JSONObject -> value.keys().forEach { visit(value.opt(it)) }
            is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            is String -> if (value.startsWith("nexus-file://")) {
                value.removePrefix("nexus-file://")
                    .substringBefore('/')
                    .substringBefore('?')
                    .takeIf { it.isNotBlank() }
                    ?.let(ids::add)
            }
        }
    }
    visit(root)
    return ids
}

private fun String.containsCardMarkdown(): Boolean =
    Regex("(\\*\\*|__|~~|`|\\[[^]]+]\\([^)]+\\)|(^|\\n)\\s*[-*+]\\s+)").containsMatchIn(this)
