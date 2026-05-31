package com.pinealctx.nexus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AdaptiveCardView(
    json: String,
    onAction: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val card = remember(json) {
        try {
            JSONObject(json)
        } catch (_: Exception) {
            null
        }
    }

    if (card == null) {
        Text("Invalid card", style = MaterialTheme.typography.bodySmall)
        return
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val body = card.optJSONArray("body")
            if (body != null) {
                RenderElements(body, onAction)
            }

            val actions = card.optJSONArray("actions")
            if (actions != null && actions.length() > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                RenderActions(actions, onAction)
            }
        }
    }
}

@Composable
private fun RenderElements(elements: JSONArray, onAction: (String, String) -> Unit) {
    for (i in 0 until elements.length()) {
        val element = elements.optJSONObject(i) ?: continue
        RenderElement(element, onAction)
        if (i < elements.length() - 1) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RenderElement(element: JSONObject, onAction: (String, String) -> Unit) {
    when (element.optString("type")) {
        "TextBlock" -> RenderTextBlock(element)
        "Image" -> RenderImage(element)
        "ColumnSet" -> RenderColumnSet(element, onAction)
        "Column" -> RenderColumn(element, onAction)
        "Container" -> RenderContainer(element, onAction)
        "ActionSet" -> {
            val actions = element.optJSONArray("actions")
            if (actions != null) RenderActions(actions, onAction)
        }
        "FactSet" -> RenderFactSet(element)
        else -> {}
    }
}

@Composable
private fun RenderTextBlock(element: JSONObject) {
    val text = element.optString("text", "")
    val size = element.optString("size", "default")
    val weight = element.optString("weight", "default")
    val isSubtle = element.optBoolean("isSubtle", false)
    val wrap = element.optBoolean("wrap", true)

    val style = when (size) {
        "extraLarge", "large" -> MaterialTheme.typography.titleLarge
        "medium" -> MaterialTheme.typography.titleMedium
        "small" -> MaterialTheme.typography.bodySmall
        else -> MaterialTheme.typography.bodyMedium
    }

    val fontWeight = when (weight) {
        "bolder" -> FontWeight.Bold
        "lighter" -> FontWeight.Light
        else -> FontWeight.Normal
    }

    val color = if (isSubtle) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface

    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = if (wrap) Int.MAX_VALUE else 1
    )
}

@Composable
private fun RenderImage(element: JSONObject) {
    val url = element.optString("url", "")
    val size = element.optString("size", "auto")

    val imageModifier = when (size) {
        "small" -> Modifier.size(40.dp)
        "medium" -> Modifier.size(80.dp)
        "large" -> Modifier.size(160.dp)
        else -> Modifier.fillMaxWidth().heightIn(max = 200.dp)
    }

    coil3.compose.AsyncImage(
        model = url,
        contentDescription = element.optString("altText", "Image"),
        modifier = imageModifier
    )
}

@Composable
private fun RenderColumnSet(element: JSONObject, onAction: (String, String) -> Unit) {
    val columns = element.optJSONArray("columns") ?: return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until columns.length()) {
            val column = columns.optJSONObject(i) ?: continue
            val width = column.optString("width", "auto")
            val columnModifier = when (width) {
                "stretch" -> Modifier.weight(1f)
                "auto" -> Modifier.wrapContentWidth()
                else -> {
                    val weight = width.removeSuffix("px").toFloatOrNull()
                    if (weight != null) Modifier.weight(weight) else Modifier.weight(1f)
                }
            }
            Column(modifier = columnModifier) {
                val items = column.optJSONArray("items")
                if (items != null) RenderElements(items, onAction)
            }
        }
    }
}

@Composable
private fun RenderColumn(element: JSONObject, onAction: (String, String) -> Unit) {
    val items = element.optJSONArray("items") ?: return
    Column {
        RenderElements(items, onAction)
    }
}

@Composable
private fun RenderContainer(element: JSONObject, onAction: (String, String) -> Unit) {
    val items = element.optJSONArray("items") ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        RenderElements(items, onAction)
    }
}

@Composable
private fun RenderFactSet(element: JSONObject) {
    val facts = element.optJSONArray("facts") ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until facts.length()) {
            val fact = facts.optJSONObject(i) ?: continue
            Row {
                Text(
                    text = fact.optString("title", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 80.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = fact.optString("value", ""),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RenderActions(actions: JSONArray, onAction: (String, String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i) ?: continue
            val title = action.optString("title", "Action")
            val type = action.optString("type", "")
            val actionData = action.optString("data", action.optString("url", ""))

            when (type) {
                "Action.Submit" -> {
                    FilledTonalButton(onClick = { onAction(type, actionData) }) {
                        Text(title)
                    }
                }
                "Action.OpenUrl" -> {
                    OutlinedButton(onClick = { onAction(type, actionData) }) {
                        Text(title)
                    }
                }
                else -> {
                    TextButton(onClick = { onAction(type, actionData) }) {
                        Text(title)
                    }
                }
            }
        }
    }
}
