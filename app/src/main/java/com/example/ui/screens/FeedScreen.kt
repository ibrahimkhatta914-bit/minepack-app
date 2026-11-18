package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FeedSortOrder
import com.example.data.model.ResourcePack
import com.example.data.model.UiState
import com.example.ui.components.PackPostCard
import com.example.ui.components.SkeletonPackCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAccent

@Composable
fun FeedScreen(
    packsState: UiState<List<ResourcePack>>,
    searchQuery: String,
    sortOrder: FeedSortOrder,
    likedPackIds: Set<String>,
    currentUserId: String,
    isAdminMode: Boolean,
    onSearchChange: (String) -> Unit,
    onSortChange: (FeedSortOrder) -> Unit,
    onAuthorClick: (String) -> Unit,
    onDownloadClick: (ResourcePack) -> Unit,
    onLikeClick: (ResourcePack) -> Unit,
    onDeleteClick: (ResourcePack) -> Unit,
    onOpenAddPack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            item(key = "search_and_filter") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feed_search_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = sortOrder == FeedSortOrder.NEWEST,
                            onClick = { onSortChange(FeedSortOrder.NEWEST) },
                            label = { Text(stringResource(R.string.filter_newest)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = EmeraldPrimary,
                                selectedLeadingIconColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("filter_newest_chip")
                        )

                        FilterChip(
                            selected = sortOrder == FeedSortOrder.MOST_DOWNLOADED,
                            onClick = { onSortChange(FeedSortOrder.MOST_DOWNLOADED) },
                            label = { Text(stringResource(R.string.filter_most_downloaded)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldAccent.copy(alpha = 0.22f),
                                selectedLabelColor = GoldAccent,
                                selectedLeadingIconColor = GoldAccent
                            ),
                            modifier = Modifier.testTag("filter_downloads_chip")
                        )
                    }
                }
            }

            when (packsState) {
                is UiState.Loading -> {
                    items(3) {
                        SkeletonPackCard()
                    }
                }

                is UiState.Error -> {
                    item(key = "error_state") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "حدث خطأ أثناء تحميل الباكات",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ErrorRed
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = packsState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val packs = packsState.data
                    if (packs.isEmpty()) {
                        item(key = "empty_feed") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.img_default_pack_1794860089473),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            "لا توجد باكات منشورة حالياً"
                                        } else {
                                            "لا توجد نتائج مطابقة لـ \"$searchQuery\""
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "كن أول من يشارك ريسورس باك رائع مع المجتمع!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = onOpenAddPack,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("نشر باك الآن", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(packs, key = { it.id }) { pack ->
                            PackPostCard(
                                pack = pack,
                                isLiked = likedPackIds.contains(pack.id),
                                canDelete = isAdminMode || pack.authorId == currentUserId,
                                onAuthorClick = onAuthorClick,
                                onDownloadClick = onDownloadClick,
                                onLikeClick = onLikeClick,
                                onDeleteClick = onDeleteClick
                            )
                        }
                    }
                }
            }
        }
    }
}
