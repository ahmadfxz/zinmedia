package com.zinmedia.photoeditor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zinmedia.photoeditor.R
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Navigation2
import compose.icons.evaicons.fill.SmilingFace
@Composable
fun BottomChatDetail(
    modifier: Modifier = Modifier,
    inputTitle: String = "Kirim pesan",
    enableButton: Boolean = true,
    onSend: (String) -> Unit,
) {

    val keyboardController =
        LocalSoftwareKeyboardController.current

    val focusManager =
        LocalFocusManager.current


    var message by rememberSaveable {
        mutableStateOf("")
    }

    var showEmojiPicker by rememberSaveable {
        mutableStateOf(false)
    }

    val emojis = remember {
        listOf(
            "😀", "😃", "😄", "😁", "😆",
            "😅", "😂", "🤣", "😊", "😇",
            "😍", "🥰", "😘", "😎", "🔥",
            "❤️", "👍", "🙏", "🎉", "😭"
        )
    }

    val isSendEnabled =
        message.isNotBlank() && enableButton

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {

        // =====================================================
        // EMOJI PICKER
        // =====================================================

        AnimatedVisibility(
            visible = showEmojiPicker
        ) {

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                items(emojis) { emoji ->

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                Color.White.copy(
                                    alpha = 0.06f
                                )
                            )
                            .clickable {

                                message += emoji
                            }
                            .padding(10.dp),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text = emoji,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }

        // =====================================================
        // INPUT BAR
        // =====================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
            verticalAlignment =
                Alignment.Bottom
        ) {

            // =====================================================
            // INPUT CONTAINER
            // =====================================================

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(
                        RoundedCornerShape(26.dp)
                    )
                    .background(
                        Color(0xFF1A1A1C)
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    ),
                verticalAlignment =
                    Alignment.Bottom
            ) {

                // =====================================================
                // EMOJI BUTTON
                // =====================================================

                Icon(
                    painter = painterResource(
                        R.drawable.ic_emote_plus
                    ),
                    contentDescription = "Emoji",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {

                            showEmojiPicker =
                                !showEmojiPicker
                        }
                )

                Spacer(
                    modifier = Modifier.width(
                        10.dp
                    )
                )

                // =====================================================
                // INPUT FIELD
                // =====================================================

                MessageInputField(
                    value = message,
                    placeholder = inputTitle,
                    onValueChange = {
                        message = it
                    },
                    modifier = Modifier.weight(
                        1f
                    )
                )
            }

            Spacer(
                modifier = Modifier.width(
                    10.dp
                )
            )

            // =====================================================
            // SEND BUTTON
            // =====================================================

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(

                        if (isSendEnabled) {

                            Color(0xFF25D366)

                        } else {

                            Color(0xFF2A2A2D)
                        }
                    )
                    .clickable(
                        enabled = isSendEnabled
                    ) {
                        keyboardController?.hide()

                        focusManager.clearFocus()

                        onSend(message)

                        message = ""
                    },
                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    painter = painterResource(
                        R.drawable.ic_send
                    ),
                    contentDescription = "Send",
                    modifier = Modifier.size(
                        22.dp
                    ),
                    tint = if (isSendEnabled) {

                        Color.White

                    } else {

                        Color.White.copy(
                            alpha = 0.35f
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageInputField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

    BasicTextField(

        value = value,

        onValueChange = {

            onValueChange(
                it.take(500)
            )
        },

        modifier = modifier,

        maxLines = 4,

        cursorBrush = SolidColor(
            Color.White
        ),

        textStyle =
            LocalTextStyle.current.copy(

                color = Color.White,

                fontSize = 15.sp,

                lineHeight = 20.sp
            ),

        decorationBox = { innerTextField ->

            Box {

                if (value.isEmpty()) {

                    Text(
                        text = placeholder,

                        color = Color.White,

                        fontSize = 15.sp
                    )
                }

                innerTextField()
            }
        }
    )
}