package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.model.ResourcePack
import com.example.data.model.UiState
import com.example.data.repository.MinePacksRepository
import com.example.ui.auth.signOutUser
import com.example.ui.components.AddPackDialog
import com.example.ui.components.MinePacksToastBanner
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.MinePacksViewModel
import com.example.ui.viewmodel.ToastEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_FEED = "feed"
const val ROUTE_PROFILE = "profile"
const val ROUTE_USER_PROFILE = "user_profile/{userId}"
const val ROUTE_ADMIN = "admin"
const val ROUTE_ABOUT = "about"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var adminUnlockedOnLogin by remember { mutableStateOf(false) }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { adminUnlocked ->
                adminUnlockedOnLogin = adminUnlocked
            }
        )
    } else {
        key(user.uid) {
            MainAuthenticatedApp(
                currentUser = user,
                initialAdminUnlocked = adminUnlockedOnLogin,
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                onSignedOut = { adminUnlockedOnLogin = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAuthenticatedApp(
    currentUser: FirebaseUser,
    initialAdminUnlocked: Boolean,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: MinePacksViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MinePacksViewModel(
                    repository = MinePacksRepository(db, Firebase.auth),
                    currentUserId = currentUser.uid,
                    initialAvatarUrl = currentUser.photoUrl?.toString(),
                    initialDisplayName = currentUser.displayName,
                    initialUserEmail = currentUser.email,
                    initialAdminUnlocked = initialAdminUnlocked
                )
            }
        }
    )

    LaunchedEffect(initialAdminUnlocked) {
        if (initialAdminUnlocked) {
            viewModel.unlockAdminSession()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_FEED

    val feedPacksState by viewModel.feedPacksState.collectAsStateWithLifecycle()
    val usersState by viewModel.allUsersState.collectAsStateWithLifecycle()
    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val likedPackIds by viewModel.likedPackIds.collectAsStateWithLifecycle()
    val isAdminMode by viewModel.isAdminMode.collectAsStateWithLifecycle()
    val needsUsernameSetup by viewModel.needsUsernameSetup.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var activeToast by remember { mutableStateOf<ToastEvent?>(null) }
    var showAddPackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { event ->
            activeToast = event
            delay(2800L)
            if (activeToast == event) {
                activeToast = null
            }
        }
    }

    val openDownloadUrl: (ResourcePack) -> Unit = { pack ->
        viewModel.recordDownload(pack)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pack.downloadUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            viewModel.showToast("تعذر فتح رابط التحميل: ${pack.downloadUrl}", isError = true)
        }
    }

    val performLogout: () -> Unit = {
        signOutUser(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = onSignedOut
        )
    }

    val allPacksList = (feedPacksState as? UiState.Success)?.data ?: emptyList()
    val allUsersList = (usersState as? UiState.Success)?.data ?: emptyList()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_minepacks_icon_1794860087114),
                                contentDescription = stringResource(R.string.cd_app_logo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(1.dp, EmeraldBright, RoundedCornerShape(9.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "MinePacks",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (isAdminMode) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = GoldAccent.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "أدمن",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GoldAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "تبديل المظهر"
                            )
                        }
                        IconButton(
                            onClick = {
                                if (currentRoute != ROUTE_ABOUT) {
                                    navController.navigate(ROUTE_ABOUT) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("about_nav_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.nav_about)
                            )
                        }
                        IconButton(
                            onClick = performLogout,
                            modifier = Modifier.testTag("topbar_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.logout_btn)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail && currentRoute != ROUTE_ABOUT) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_FEED,
                            onClick = {
                                navController.navigate(ROUTE_FEED) {
                                    popUpTo(ROUTE_FEED) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == ROUTE_FEED) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.tab_feed)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldBright,
                                selectedTextColor = EmeraldBright,
                                indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("nav_feed")
                        )

                        NavigationBarItem(
                            selected = currentRoute == ROUTE_PROFILE,
                            onClick = {
                                navController.navigate(ROUTE_PROFILE) {
                                    popUpTo(ROUTE_FEED)
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == ROUTE_PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(R.string.tab_profile)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldBright,
                                selectedTextColor = EmeraldBright,
                                indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("nav_profile")
                        )

                        if (isAdminMode) {
                            NavigationBarItem(
                                selected = currentRoute == ROUTE_ADMIN,
                                onClick = {
                                    navController.navigate(ROUTE_ADMIN) {
                                        popUpTo(ROUTE_FEED)
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (currentRoute == ROUTE_ADMIN) Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_admin)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GoldAccent,
                                    selectedTextColor = GoldAccent,
                                    indicatorColor = GoldAccent.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.testTag("nav_admin")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute == ROUTE_FEED || currentRoute == ROUTE_PROFILE) {
                    FloatingActionButton(
                        onClick = { showAddPackDialog = true },
                        containerColor = EmeraldPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_add_pack")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_pack_title))
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail && currentRoute != ROUTE_ABOUT) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            NavigationRailItem(
                                selected = currentRoute == ROUTE_FEED,
                                onClick = {
                                    navController.navigate(ROUTE_FEED) {
                                        popUpTo(ROUTE_FEED) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (currentRoute == ROUTE_FEED) Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_feed)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldBright,
                                    selectedTextColor = EmeraldBright,
                                    indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                                )
                            )
                            NavigationRailItem(
                                selected = currentRoute == ROUTE_PROFILE,
                                onClick = {
                                    navController.navigate(ROUTE_PROFILE) {
                                        popUpTo(ROUTE_FEED)
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (currentRoute == ROUTE_PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                                        contentDescription = null
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_profile)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldBright,
                                    selectedTextColor = EmeraldBright,
                                    indicatorColor = EmeraldPrimary.copy(alpha = 0.2f)
                                )
                            )
                            if (isAdminMode) {
                                NavigationRailItem(
                                    selected = currentRoute == ROUTE_ADMIN,
                                    onClick = {
                                        navController.navigate(ROUTE_ADMIN) {
                                            popUpTo(ROUTE_FEED)
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (currentRoute == ROUTE_ADMIN) Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings,
                                            contentDescription = null
                                        )
                                    },
                                    label = { Text(stringResource(R.string.tab_admin)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = GoldAccent,
                                        selectedTextColor = GoldAccent,
                                        indicatorColor = GoldAccent.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_FEED,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        composable(ROUTE_FEED) {
                            FeedScreen(
                                packsState = feedPacksState,
                                searchQuery = searchQuery,
                                sortOrder = sortOrder,
                                likedPackIds = likedPackIds,
                                currentUserId = currentUser.uid,
                                isAdminMode = isAdminMode,
                                onSearchChange = viewModel::updateSearchQuery,
                                onSortChange = viewModel::updateSortOrder,
                                onAuthorClick = { authorId ->
                                    if (authorId == currentUser.uid) {
                                        navController.navigate(ROUTE_PROFILE)
                                    } else {
                                        navController.navigate("user_profile/$authorId")
                                    }
                                },
                                onDownloadClick = openDownloadUrl,
                                onLikeClick = viewModel::toggleLike,
                                onDeleteClick = viewModel::deletePack,
                                onOpenAddPack = { showAddPackDialog = true }
                            )
                        }

                        composable(ROUTE_PROFILE) {
                            val myPacks = remember(allPacksList, currentUser.uid) {
                                allPacksList.filter { it.authorId == currentUser.uid }
                            }
                            ProfileScreen(
                                targetUserId = currentUser.uid,
                                isOwnProfile = true,
                                isAdminMode = isAdminMode,
                                profile = currentUserProfile,
                                fallbackName = currentUser.displayName ?: "لاعب ماين كرافت",
                                fallbackAvatarUrl = currentUser.photoUrl?.toString(),
                                userPacks = myPacks,
                                onEditUsername = viewModel::saveUsername,
                                onDownloadClick = openDownloadUrl,
                                onDeletePackClick = viewModel::deletePack,
                                onLogoutClick = performLogout
                            )
                        }

                        composable(
                            route = ROUTE_USER_PROFILE,
                            arguments = listOf(navArgument("userId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val targetUid = backStackEntry.arguments?.getString("userId") ?: currentUser.uid
                            val targetProfile = remember(allUsersList, targetUid) {
                                allUsersList.firstOrNull { it.userId == targetUid }
                            }
                            val targetPacks = remember(allPacksList, targetUid) {
                                allPacksList.filter { it.authorId == targetUid }
                            }
                            ProfileScreen(
                                targetUserId = targetUid,
                                isOwnProfile = targetUid == currentUser.uid,
                                isAdminMode = isAdminMode,
                                profile = targetProfile,
                                fallbackName = targetPacks.firstOrNull()?.authorName ?: "لاعب ماين كرافت",
                                fallbackAvatarUrl = targetPacks.firstOrNull()?.authorAvatarUrl,
                                userPacks = targetPacks,
                                onBack = { navController.popBackStack() },
                                onEditUsername = viewModel::saveUsername,
                                onDownloadClick = openDownloadUrl,
                                onDeletePackClick = viewModel::deletePack,
                                onLogoutClick = performLogout
                            )
                        }

                        composable(ROUTE_ADMIN) {
                            AdminScreen(
                                packsState = feedPacksState,
                                usersState = usersState,
                                currentUserId = currentUser.uid,
                                onAddPackClick = { showAddPackDialog = true },
                                onDeletePack = viewModel::deletePack,
                                onDeleteUser = viewModel::deleteUser
                            )
                        }

                        composable(ROUTE_ABOUT) {
                            AboutScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }

                // Floating animated Toast Banner
                MinePacksToastBanner(
                    toastEvent = activeToast,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                )
            }
        }
    }

    if (showAddPackDialog) {
        AddPackDialog(
            isBusy = isBusy,
            onDismiss = { showAddPackDialog = false },
            onPublish = { title, desc, img, dl ->
                viewModel.publishPack(
                    title = title,
                    description = desc,
                    imageUrl = img,
                    downloadUrl = dl,
                    onPublished = { showAddPackDialog = false }
                )
            }
        )
    }

    if (needsUsernameSetup) {
        var chosenUsername by remember {
            mutableStateOf(currentUser.displayName?.take(30) ?: "")
        }
        AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.username_prompt_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.username_prompt_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = chosenUsername,
                        onValueChange = { chosenUsername = it },
                        label = { Text("اسم المستخدم") },
                        placeholder = { Text("مثال: StevePro") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_setup_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveUsername(chosenUsername) },
                    enabled = !isBusy && chosenUsername.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.testTag("username_setup_save_button")
                ) {
                    Text("حفظ والمتابعة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
