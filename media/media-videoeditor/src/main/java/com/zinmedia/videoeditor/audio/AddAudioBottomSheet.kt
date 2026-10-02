package com.zinmedia.videoeditor.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.PlayCircle
import compose.icons.evaicons.fill.Plus
import compose.icons.evaicons.fill.Search

data class AudioModel(
    val id: String,
    val title: String,
    val artist: String,
    val url: String
)

val sampleAudios = listOf(
    AudioModel("1", "Dj viral tiktok", "DJ Breeze", "https://storage.googleapis.com/jualxbeli/mp3/a.mp3"),
    AudioModel("2", "Dj terbaru slow","Dj Rimex", "https://storage.googleapis.com/jualxbeli/mp3/b.mp3"),
    AudioModel("3", "Ayang", "Ajeng Frebria", "https://storage.googleapis.com/jualxbeli/mp3/c.mp3"),
)


@Composable
fun AddAudioBottomSheet(
    modifier: Modifier = Modifier,
    audios: List<AudioModel> = sampleAudios,     // bisa kamu ganti
    onSelect: (AudioModel) -> Unit = {},
    onPlay: (AudioModel) -> Unit = {},
) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Trending", "Favorites", "For You", "Viral")

    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            // 🔍 Search Bar
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search music") },
                leadingIcon = {
                    Icon(EvaIcons.Fill.Search, contentDescription = null)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 🔥 Category Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    AssistChip(
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        shape = RoundedCornerShape(20.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (cat == selectedCategory)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        border = null,
                        leadingIcon = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))


            // 🎵 Audio List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxHeight(0.8f)
            ) {
                items(audios.filter { it.title.contains(search, true) }) { audio ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        // Thumbnail
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Gray.copy(0.3f))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(audio.title, fontWeight = FontWeight.Bold)
                            Text(audio.artist, fontSize = 12.sp, color = Color.Gray)

                            Spacer(Modifier.height(6.dp))

                            // Waveform dummy
                            Box(
                                modifier = Modifier
                                    .height(16.dp)
                                    .fillMaxWidth(0.7f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.LightGray.copy(0.4f))
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(horizontalAlignment = Alignment.End) {

                            // ▶ Play Button
                            IconButton(onClick = { onPlay(audio) }) {
                                Icon(
                                    EvaIcons.Fill.PlayCircle,
                                    contentDescription = "Play"
                                )
                            }

                            // Use Button
                            TextButton(
                                onClick = { onSelect(audio) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Use")
                            }
                        }
                    }
                }
            }
        }

}
