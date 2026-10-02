package com.jernih.videoeditor.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jernih.media.videoeditor.R

@Composable
fun PlayerControls(
    onFilter: () -> Unit,
    onExport: () -> Unit,
    onSticker: () -> Unit,
    onText: () -> Unit,
    onTrim: () -> Unit,
    exporting: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Spacer(modifier = Modifier.width(8.dp))

//        ControlIcon(
//            icon =  EvaIcons.Outline.MinusSquare,
//            contentDescription = "Trim Controls",
//            onClick = onTrim
//        )
//
//        Spacer(modifier = Modifier.width(12.dp))

        ControlIcon(
            icon =  R.drawable.ic_filter,
            contentDescription = "Add Filter",
            onClick = onFilter
        )

        Spacer(modifier = Modifier.width(12.dp))

        ControlIcon(
            icon = R.drawable.ic_text,
            contentDescription = "Add Text",
            onClick =  onText
        )

        Spacer(modifier = Modifier.width(12.dp))

        ControlIcon(
            icon = R.drawable.ic_sticker,
            contentDescription = "Add Sticker",
            onClick = onSticker
        )

        Spacer(modifier = Modifier.weight(1f))

        ExportButton(
            exporting = exporting,
            onClick = onExport
        )
    }
}

@Composable
fun OverlayControls(
    onFilter: () -> Unit,
    onSticker: () -> Unit,
    onText: () -> Unit,
    onDraw: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row (
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(vertical = 6.dp, horizontal = 6.dp)
    ) {
        ControlIcon(
            icon = R.drawable.ic_close,
            contentDescription = "Add Text",
            onClick =  onDismiss,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Spacer(Modifier.weight(1f))
        ControlIcon(
            icon =  R.drawable.ic_filter,
            contentDescription = "Add Effect",
            onClick = onFilter
        )
        ControlIcon(
            icon = R.drawable.ic_text,
            contentDescription = "Add Text",
            onClick =  onText
        )
        ControlIcon(
            icon = R.drawable.ic_sticker,
            contentDescription = "Add Sticker",
            onClick = onSticker
        )

        ControlIcon(
            icon = R.drawable.ic_draw,
            contentDescription = "Add Draw",
            onClick = onDraw
        )
    }
}

@Composable
fun ControlIcon(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable { onClick() }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ExportButton(
    exporting: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !exporting,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background
        )
    ) {
        Text(
            text = if (exporting) "Processing..." else "Selesai"
        )
    }
}