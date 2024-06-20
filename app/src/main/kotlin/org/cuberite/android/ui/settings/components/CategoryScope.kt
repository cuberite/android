package org.cuberite.android.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.cuberite.android.ui.theme.CuberiteTheme

@Immutable
interface CategoryScope {

    val title: String

    val description: String?

    val icon: ImageVector?

    @Stable
    fun Modifier.categoryItem(): Modifier

}

@Composable
fun Category(
    data: CategoryScope,
    modifier: Modifier = Modifier,
    content: @Composable CategoryScope.() -> Unit,
) {
    with(data) {
        Column(modifier = modifier) {
            Header()
            content()
        }
    }
}

@Composable
private fun CategoryScope.Header(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .wrapContentSize()
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = requireNotNull(icon),
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1F)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (description != null) {
                Text(
                    text = requireNotNull(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun CategoryScope.Footer(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.categoryItem(),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
fun CategoryScope.SwitchItem(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable { onCheckedChange(!isChecked) }
            .categoryItem(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1F),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CategoryScope.DialogItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .categoryItem(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (description != null) {
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun rememberCategory(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
): CategoryScope = CategoryData(
    title = title,
    description = description,
    icon = icon,
)

class CategoryData(
    override val title: String,
    override val description: String? = null,
    override val icon: ImageVector? = null,
) : CategoryScope {

    override fun Modifier.categoryItem(): Modifier =
        this
            .fillMaxWidth()
            .padding(start = 64.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)

}

@Preview(showBackground = true)
@Composable
private fun HeaderPreview() {
    CuberiteTheme {
        val data = rememberCategory(
            title = "Settings",
            icon = Icons.Rounded.Settings,
            description = "Use this section from time to time to update your Cuberite installation",
        )

        Category(data = data) {
            var isChecked by remember {
                mutableStateOf(false)
            }
            SwitchItem(
                title = "Start Cuberite on boot",
                description = "Automatically start Cuberite when your device starts",
                isChecked = isChecked,
                onCheckedChange = { isChecked = it }
            )
            DialogItem(
                title = "Theme",
                description = "System",
                onClick = { },
            )
            DialogItem(
                title = "Change login credentials",
                onClick = { },
            )
        }
    }
}
