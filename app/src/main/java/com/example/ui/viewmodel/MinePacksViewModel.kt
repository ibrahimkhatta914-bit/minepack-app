package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FeedSortOrder
import com.example.data.model.ResourcePack
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.MinePacksRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ToastEvent(
    val message: String,
    val isError: Boolean = false
)

class MinePacksViewModel(
    private val repository: MinePacksRepository,
    val currentUserId: String,
    private val initialAvatarUrl: String?,
    private val initialDisplayName: String?,
    private val initialUserEmail: String?,
    initialAdminUnlocked: Boolean
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(FeedSortOrder.NEWEST)
    val sortOrder: StateFlow<FeedSortOrder> = _sortOrder.asStateFlow()

    private val _likedPackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedPackIds: StateFlow<Set<String>> = _likedPackIds.asStateFlow()

    private val _isAdminMode = MutableStateFlow(
        initialAdminUnlocked ||
            initialUserEmail.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)
    )
    val isAdminMode: StateFlow<Boolean> = _isAdminMode.asStateFlow()

    private val _needsUsernameSetup = MutableStateFlow(false)
    val needsUsernameSetup: StateFlow<Boolean> = _needsUsernameSetup.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<ToastEvent> = _toastEvents.asSharedFlow()

    val currentUserProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            Log.w(TAG, "Failed observing current user profile", unwrapFirestoreError(error))
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val rawPacksState: StateFlow<UiState<List<ResourcePack>>> = repository.observePublicPacks()
        .map<List<ResourcePack>, UiState<List<ResourcePack>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            if (root == null && error is CancellationException) throw error
            Log.w(TAG, "Failed observing packs", root ?: error)
            emit(UiState.Error((root ?: error).localizedMessage ?: "تعذر تحميل الريسورس باكات"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val feedPacksState: StateFlow<UiState<List<ResourcePack>>> = combine(
        rawPacksState,
        _searchQuery,
        _sortOrder
    ) { state, query, sort ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val filtered = if (query.isBlank()) {
                    state.data
                } else {
                    val needle = query.trim().lowercase()
                    state.data.filter { pack ->
                        pack.title.lowercase().contains(needle) ||
                            pack.authorName.lowercase().contains(needle)
                    }
                }
                val sorted = when (sort) {
                    FeedSortOrder.NEWEST -> filtered.sortedByDescending {
                        it.createdAt ?: Timestamp(0, 0)
                    }
                    FeedSortOrder.MOST_DOWNLOADED -> filtered.sortedWith(
                        compareByDescending<ResourcePack> { it.downloadsCount }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                UiState.Success(sorted)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val allUsersState: StateFlow<UiState<List<UserProfile>>> = repository.observePublicUsers()
        .map<List<UserProfile>, UiState<List<UserProfile>>> { users ->
            UiState.Success(users.sortedByDescending { it.createdAt ?: Timestamp(0, 0) })
        }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            if (root == null && error is CancellationException) throw error
            Log.w(TAG, "Failed observing users", root ?: error)
            emit(UiState.Error((root ?: error).localizedMessage ?: "تعذر تحميل المستخدمين"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        checkProfileExists()
        refreshLikedStatuses()
    }

    private fun checkProfileExists() {
        viewModelScope.launch {
            val result = repository.getUserProfile(currentUserId)
            result.onSuccess { profile ->
                if (profile == null) {
                    _needsUsernameSetup.value = true
                }
            }
        }
    }

    private fun refreshLikedStatuses() {
        viewModelScope.launch {
            rawPacksState.collect { state ->
                if (state is UiState.Success) {
                    val liked = mutableSetOf<String>()
                    state.data.forEach { pack ->
                        if (repository.isPackLikedByCurrentUser(pack.id)) {
                            liked.add(pack.id)
                        }
                    }
                    _likedPackIds.value = liked
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOrder(sortOrder: FeedSortOrder) {
        _sortOrder.value = sortOrder
    }

    fun unlockAdminSession() {
        _isAdminMode.value = true
        emitToast("تم تفعيل صلاحيات الأدمن بنجاح")
    }

    fun saveUsername(username: String) {
        val clean = username.trim()
        if (clean.length < 2) {
            emitToast("اسم المستخدم يجب أن يكون حرفين على الأقل", isError = true)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.upsertUserProfile(clean, initialAvatarUrl)
            _isBusy.value = false
            result.onSuccess {
                _needsUsernameSetup.value = false
                emitToast("تم حفظ اسم المستخدم: $clean")
            }.onFailure { err ->
                emitToast(err.localizedMessage ?: "تعذر حفظ اسم المستخدم", isError = true)
            }
        }
    }

    fun dismissUsernameSetup() {
        val fallback = initialDisplayName?.trim()?.takeIf { it.length >= 2 } ?: "لاعب MinePacks"
        saveUsername(fallback)
    }

    fun publishPack(
        title: String,
        description: String,
        imageUrl: String,
        downloadUrl: String,
        onPublished: () -> Unit
    ) {
        val cleanTitle = title.trim()
        val cleanImage = imageUrl.trim()
        val cleanDownload = downloadUrl.trim()

        if (cleanTitle.length < 2) {
            emitToast("اكتب اسم الباك (حرفين على الأقل)", isError = true)
            return
        }
        if (cleanImage.isBlank()) {
            emitToast("لا يمكن نشر باك بدون صورة", isError = true)
            return
        }
        if (!cleanDownload.startsWith("http://") && !cleanDownload.startsWith("https://")) {
            emitToast("لا يمكن نشر باك بدون رابط تحميل صحيح يبدأ بـ https://", isError = true)
            return
        }

        val profile = currentUserProfile.value
        val authorName = profile?.username?.takeIf { it.isNotBlank() }
            ?: initialDisplayName?.takeIf { it.isNotBlank() }
            ?: "لاعب ماين كرافت"
        val authorAvatar = profile?.avatarUrl ?: initialAvatarUrl

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createPack(
                title = cleanTitle,
                description = description,
                imageUrl = cleanImage,
                downloadUrl = cleanDownload,
                authorName = authorName,
                authorAvatarUrl = authorAvatar
            )
            _isBusy.value = false
            result.onSuccess {
                emitToast("تم نشر الباك بنجاح!")
                onPublished()
            }.onFailure { err ->
                emitToast(err.localizedMessage ?: "فشل نشر الباك", isError = true)
            }
        }
    }

    fun recordDownload(pack: ResourcePack) {
        emitToast("جاري فتح رابط تحميل ${pack.title}...")
        viewModelScope.launch {
            repository.incrementDownloadCount(pack.id)
                .onFailure { err ->
                    Log.w(TAG, "Download counter increment failed: ${err.message}")
                }
        }
    }

    fun toggleLike(pack: ResourcePack) {
        val currentlyLiked = _likedPackIds.value.contains(pack.id)
        // Optimistic update
        _likedPackIds.value = if (currentlyLiked) {
            _likedPackIds.value - pack.id
        } else {
            _likedPackIds.value + pack.id
        }
        viewModelScope.launch {
            val result = repository.toggleLike(pack.id, currentlyLiked)
            result.onFailure { err ->
                // Revert on failure
                _likedPackIds.value = if (currentlyLiked) {
                    _likedPackIds.value + pack.id
                } else {
                    _likedPackIds.value - pack.id
                }
                emitToast(err.localizedMessage ?: "تعذر تحديث الإعجاب", isError = true)
            }
        }
    }

    fun deletePack(pack: ResourcePack) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deletePack(pack.id)
            _isBusy.value = false
            result.onSuccess {
                emitToast("تم حذف الباك بنجاح")
            }.onFailure { err ->
                emitToast(err.localizedMessage ?: "ليس لديك صلاحية لحذف هذا الباك", isError = true)
            }
        }
    }

    fun deleteUser(user: UserProfile) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteUser(user.userId)
            _isBusy.value = false
            result.onSuccess {
                emitToast("تم حذف المستخدم ${user.username}")
            }.onFailure { err ->
                emitToast(err.localizedMessage ?: "ليس لديك صلاحية لحذف المستخدمين", isError = true)
            }
        }
    }

    fun showToast(message: String, isError: Boolean = false) {
        emitToast(message, isError)
    }

    private fun emitToast(message: String, isError: Boolean = false) {
        _toastEvents.tryEmit(ToastEvent(message = message, isError = isError))
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "MinePacksVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val BOOTSTRAP_ADMIN_EMAIL = "ibrahimkhatta914@gmail.com"
    }
}
