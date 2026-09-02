package com.pinealctx.nexus.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pinealctx.nexus.R
import java.io.File

@Composable
fun MediaPickerBar(
    onImageSelected: (Uri) -> Unit,
    onFileSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onImageSelected(it) } }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onFileSelected(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingCameraUri?.let(onImageSelected)
        pendingCameraUri = null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MediaPickerButton(
                icon = Icons.Filled.CameraAlt,
                label = stringResource(R.string.media_camera),
                onClick = {
                    val cameraDirectory = File(context.cacheDir, "camera").apply { mkdirs() }
                    val output = File(cameraDirectory, "photo_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        output
                    )
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }
            )
            MediaPickerButton(
                icon = Icons.Filled.Image,
                label = stringResource(R.string.media_gallery),
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            )
            MediaPickerButton(
                icon = Icons.Filled.AttachFile,
                label = stringResource(R.string.media_file),
                onClick = { filePicker.launch("*/*") }
            )
        }
    }
}

@Composable
private fun MediaPickerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
