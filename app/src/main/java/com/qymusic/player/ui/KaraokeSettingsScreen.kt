package com.qymusic.player.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R

@Composable
internal fun KaraokeSettingsScreen(
    outputTreeUri: String?,
    keepDraftAfterPublish: Boolean,
    onOutputTreeChange: (String?) -> Unit,
    onKeepDraftAfterPublishChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val outputTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onOutputTreeChange(uri.toString())
        }
    }

    BackHandler(onBack = onBack)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.karaoke_generation_settings),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.karaoke_generation_path),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = outputTreeUri?.let(::displayTreeName)
                                    ?: stringResource(
                                        R.string.karaoke_generation_path_default,
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { outputTreePicker.launch(null) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.karaoke_choose_output_directory,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (outputTreeUri != null) {
                                    OutlinedButton(
                                        onClick = { onOutputTreeChange(null) },
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.karaoke_use_default_output,
                                            ),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) {
                            onKeepDraftAfterPublishChange(!keepDraftAfterPublish)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.keep_karaoke_draft_after_publish),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = keepDraftAfterPublish,
                        onCheckedChange = onKeepDraftAfterPublishChange,
                    )
                }
            }
        }
    }
}

private fun displayTreeName(uri: String): String =
    runCatching {
        Uri.decode(uri).substringAfterLast('/').ifBlank { uri }
    }.getOrDefault(uri)
