package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ResourcePack
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.components.rememberPackImageModel
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NetherPurple

@Composable
fun AdminScreen(
    packsState: UiState<List<ResourcePack>>,
    usersState: UiState<List<UserProfile>>,
    currentUserId: String,
    onAddPackClick: () -> Unit,
    onDeletePack: (ResourcePack) -> Unit,
    onDeleteUser: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var packToDelete by remember { mutableStateOf<ResourcePack?>(null) }
    var userToDelete by remember { mutableStateOf<UserProfile?>(null) }

    val packs = (packsState as? UiState.Success)?.data ?: emptyList()
    val users = (usersState as? UiState.Success)?.data ?: emptyList()
    val totalDownloads = packs.sumOf { it.downloadsCount }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item(key = "admin_header") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "لوحة تحكم الأدمن",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "إدارة الباكات والمستخدمين والإحصائيات",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AdminStatTile(
                                icon = Icons.Default.Group,
                                title = "المستخدمين",
                                count = users.size.toString(),
                                color = NetherPurple,
                                modifier = Modifier.weight(1f)
                            )
                            AdminStatTile(
                                icon = Icons.Default.Inventory2,
                                title = "الباكات",
                                count = packs.size.toString(),
                                color = EmeraldBright,
                                modifier = Modifier.weight(1f)
                            )
                            AdminStatTile(
                                icon = Icons.Default.CloudDownload,
                                title = "التحميلات",
                                count = totalDownloads.toString(),
                                color = GoldAccent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = onAddPackClick,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_add_pack_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "إضافة باك جديد",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item(key = "admin_tabs") {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = EmeraldPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الباكات (${packs.size})", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("admin_packs_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("المستخدمون (${users.size})", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("admin_users_tab")
                    )
                }
            }

            if (selectedTab == 0) {
                if (packs.isEmpty()) {
                    item {
                        Text(
                            text = "لا توجد باكات حالياً.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(packs, key = { it.id }) { pack ->
                        AdminPackRow(
                            pack = pack,
                            onDeleteClick = { packToDelete = pack }
                        )
                    }
                }
            } else {
                if (users.isEmpty()) {
                    item {
                        Text(
                            text = "لا يوجد مستخدمون مسجلون حالياً.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(users, key = { it.userId }) { user ->
                        AdminUserRow(
                            user = user,
                            isCurrentUser = user.userId == currentUserId,
                            onDeleteClick = { userToDelete = user }
                        )
                    }
                }
            }
        }
    }

    packToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { packToDelete = null },
            title = { Text("حذف الباك") },
            text = { Text("هل أنت متأكد من حذف \"${target.title}\"؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePack(target)
                        packToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { packToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    userToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("حذف المستخدم") },
            text = { Text("هل تريد حذف ملف المستخدم \"${target.username}\"؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(target)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun AdminStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminPackRow(
    pack: ResourcePack,
    onDeleteClick: () -> Unit
) {
    val imageModel = rememberPackImageModel(pack.imageUrl)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = pack.title,
                placeholder = painterResource(R.drawable.img_default_pack_1794860089473),
                error = painterResource(R.drawable.img_default_pack_1794860089473),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "بواسطة: ${pack.authorName} • ${pack.downloadsCount} تحميل",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("admin_delete_pack_${pack.id}")
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الباك", tint = ErrorRed)
            }
        }
    }
}

@Composable
private fun AdminUserRow(
    user: UserProfile,
    isCurrentUser: Boolean,
    onDeleteClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            UserAvatar(
                avatarUrl = user.avatarUrl,
                username = user.username,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentUser) "${user.username} (أنت)" else user.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "الباكات: ${user.packsCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isCurrentUser) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("admin_delete_user_${user.userId}")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف المستخدم", tint = ErrorRed)
                }
            }
        }
    }
}
