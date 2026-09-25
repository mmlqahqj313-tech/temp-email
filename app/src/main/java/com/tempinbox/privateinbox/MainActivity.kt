package com.tempinbox.privateinbox
import androidx.compose.ui.text.style.TextDirection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Background = Color(0xFF050A10)
private val SurfaceDark = Color(0xFF08131F)
private val SurfaceDark2 = Color(0xFF0B1B2A)
private val SurfaceDark3 = Color(0xFF0E2234)
private val Blue = Color(0xFF2D8CFF)
private val Cyan = Color(0xFF5FC7FF)
private val White = Color(0xFFF7FAFF)
private val Muted = Color(0xFF9CAEC2)
private val Danger = Color(0xFFFF6B6B)
private val Success = Color(0xFF62D9A2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                TempEmailTheme {
                    TempEmailApp()
                }
            }
        }
    }
}

@Composable
private fun TempEmailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Background,
            surface = SurfaceDark,
            primary = Blue,
            secondary = Cyan,
            onPrimary = Color.White,
            onBackground = White,
            onSurface = White
        ),
        content = content
    )
}

private class TempEmailViewModel : ViewModel() {
    var account by mutableStateOf<MailAccount?>(null)
        private set
    var messages by mutableStateOf<List<MailSummary>>(emptyList())
        private set
    var selectedMessage by mutableStateOf<MailDetails?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var remainingMillis by mutableLongStateOf(0L)
        private set
    var autoRefresh by mutableStateOf(true)
        private set
    var initialized by mutableStateOf(false)
        private set

    private val operationMutex = Mutex()
    private var api: MailTmApi? = null
    private var store: SecureSessionStore? = null

    fun initialize(context: Context) {
        if (initialized) return

        api = MailTmApi()
        store = SecureSessionStore(context.applicationContext)
        autoRefresh = store?.getAutoRefresh() ?: true

        val loaded = store?.load()
        if (loaded != null && loaded.expiresAtMillis > System.currentTimeMillis()) {
            account = loaded
            remainingMillis = loaded.expiresAtMillis - System.currentTimeMillis()
        } else {
            store?.clearAccount()
        }

        initialized = true
    }

    suspend fun ensureMailbox() {
        operationMutex.withLock {
            if (!initialized) return

            val current = account
            when {
                current == null -> createNewAccountInternal()
                current.expiresAtMillis <= System.currentTimeMillis() -> expireCurrentAccountInternal()
                else -> refreshMessagesInternal(silent = false)
            }
        }
    }

    suspend fun createNewAccountSuspend() {
        operationMutex.withLock {
            createNewAccountInternal()
        }
    }

    suspend fun refreshMessages(silent: Boolean = false) {
        operationMutex.withLock {
            refreshMessagesInternal(silent)
        }
    }

    suspend fun tickAndExpireIfNeeded() {
        val current = account ?: return
        val remaining = current.expiresAtMillis - System.currentTimeMillis()
        remainingMillis = remaining.coerceAtLeast(0L)

        if (remaining <= 0L && !busy) {
            expireCurrentAccount()
        }
    }

    suspend fun openMessage(summary: MailSummary) {
        operationMutex.withLock {
            val localApi = api ?: return
            if (account == null) return

            busy = true
            error = null

            try {
                val detail = authenticatedCall { token ->
                    withContext(Dispatchers.IO) {
                        localApi.markAsRead(token, summary.id)
                        localApi.getMessage(token, summary.id)
                    }
                }

                messages = messages.map {
                    if (it.id == summary.id) it.copy(seen = true) else it
                }
                selectedMessage = detail.copy(seen = true)
            } catch (t: Throwable) {
                handleOperationFailure(t)
            } finally {
                busy = false
            }
        }
    }

    suspend fun deleteMessage(messageId: String) {
        operationMutex.withLock {
            val localApi = api ?: return
            if (account == null) return

            busy = true
            error = null

            try {
                authenticatedCall { token ->
                    withContext(Dispatchers.IO) {
                        localApi.deleteMessage(token, messageId)
                    }
                }
                messages = messages.filterNot { it.id == messageId }
                selectedMessage = null
            } catch (t: Throwable) {
                if (t is MailTmException && t.status == 404) {
                    messages = messages.filterNot { it.id == messageId }
                    selectedMessage = null
                    error = null
                } else {
                    handleOperationFailure(t)
                }
            } finally {
                busy = false
            }
        }
    }

    suspend fun expireCurrentAccount() {
        operationMutex.withLock {
            expireCurrentAccountInternal()
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        autoRefresh = enabled
        store?.setAutoRefresh(enabled)
    }

    fun clearSelectedMessage() {
        selectedMessage = null
    }

    private suspend fun createNewAccountInternal() {
        val localApi = api ?: return
        val localStore = store ?: return

        busy = true
        error = null

        try {
            val newAccount = withContext(Dispatchers.IO) {
                localApi.createAccount()
            }

            val old = account
            account = newAccount
            remainingMillis = newAccount.expiresAtMillis - System.currentTimeMillis()
            messages = emptyList()
            selectedMessage = null
            localStore.save(newAccount)

            if (old != null) {
                deleteAccountBestEffort(old)
            }
        } catch (t: Throwable) {
            error = friendlyError(t)
        } finally {
            busy = false
        }
    }

    private suspend fun refreshMessagesInternal(silent: Boolean) {
        val localApi = api ?: return
        if (account == null) {
            if (!silent) createNewAccountInternal()
            return
        }

        if (!silent) {
            busy = true
            error = null
        }

        try {
            val result = authenticatedCall { token ->
                withContext(Dispatchers.IO) {
                    localApi.listMessages(token)
                }
            }
            messages = result
            error = null
        } catch (t: Throwable) {
            if (t is MailTmException && t.status == 401) {
                recoverSessionInternal()
            } else if (!silent) {
                error = friendlyError(t)
            }
        } finally {
            if (!silent) busy = false
        }
    }

    private suspend fun <T> authenticatedCall(action: suspend (String) -> T): T {
        val localApi = api ?: throw MailTmException(null, "خدمة البريد غير مهيأة.")
        val current = account ?: throw MailTmException(null, "لا يوجد عنوان بريد حالي.")

        try {
            return action(current.token)
        } catch (t: Throwable) {
            if (t !is MailTmException || t.status != 401) throw t

            val refreshedToken = withContext(Dispatchers.IO) {
                localApi.authenticate(current.address, current.password)
            }
            val renewed = current.copy(token = refreshedToken)
            account = renewed
            store?.save(renewed)
            return action(refreshedToken)
        }
    }

    private suspend fun recoverSessionInternal() {
        account = null
        messages = emptyList()
        selectedMessage = null
        remainingMillis = 0L
        store?.clearAccount()
        createNewAccountInternal()
    }

    private suspend fun expireCurrentAccountInternal() {
        val current = account ?: return
        if (current.expiresAtMillis > System.currentTimeMillis()) {
            remainingMillis = current.expiresAtMillis - System.currentTimeMillis()
            return
        }

        deleteAccountBestEffort(current)

        account = null
        messages = emptyList()
        selectedMessage = null
        remainingMillis = 0L
        store?.clearAccount()

        createNewAccountInternal()
    }

    private suspend fun deleteAccountBestEffort(target: MailAccount) {
        val localApi = api ?: return

        try {
            withContext(Dispatchers.IO) {
                localApi.deleteAccount(target.token, target.id)
            }
        } catch (first: Throwable) {
            if (first is MailTmException && first.status == 401) {
                runCatching {
                    val refreshedToken = withContext(Dispatchers.IO) {
                        localApi.authenticate(target.address, target.password)
                    }
                    withContext(Dispatchers.IO) {
                        localApi.deleteAccount(refreshedToken, target.id)
                    }
                }
            }
        }
    }

    private suspend fun handleOperationFailure(t: Throwable) {
        if (t is MailTmException && t.status == 401) {
            recoverSessionInternal()
        } else {
            error = friendlyError(t)
        }
    }
}

@Composable
private fun TempEmailApp(vm: TempEmailViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by remember { mutableStateOf(0) }
    var showPrivacy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.initialize(context)
        vm.ensureMailbox()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.tickAndExpireIfNeeded()
                delay(1_000)
            }
        }
    }

    LaunchedEffect(lifecycleOwner, vm.autoRefresh, vm.account?.id) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (vm.autoRefresh && vm.account != null) {
                vm.refreshMessages(silent = true)
                while (true) {
                    delay(20_000)
                    vm.refreshMessages(silent = true)
                }
            }
        }
    }

    if (vm.selectedMessage != null) {
        MessageDetailScreen(
            message = vm.selectedMessage!!,
            busy = vm.busy,
            onBack = vm::clearSelectedMessage,
            onDelete = {
                vm.selectedMessage?.let { message ->
                    scope.launch { vm.deleteMessage(message.id) }
                }
            }
        )
        return
    }

    if (showPrivacy) {
        PrivacyScreen(onBack = { showPrivacy = false })
        return
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Color(0xFF07101A)
            ) {
                val navItems = listOf(
                    Triple("الرئيسية", Icons.Default.Home, 0),
                    Triple("الوارد", Icons.Default.Inbox, 1),
                    Triple("العنوان", Icons.Default.AlternateEmail, 2),
                    Triple("المزيد", Icons.Default.MoreHoriz, 3)
                )

                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                account = vm.account,
                messages = vm.messages,
                busy = vm.busy,
                error = vm.error,
                remainingMillis = vm.remainingMillis,
                onCopy = { copyText(context, vm.account?.address.orEmpty()) },
                onShare = { shareText(context, vm.account?.address.orEmpty()) },
                onCreate = { scope.launch { vm.createNewAccountSuspend() } },
                onRefresh = { scope.launch { vm.refreshMessages() } },
                onOpenInbox = { selectedTab = 1 },
                modifier = Modifier.padding(padding)
            )

            1 -> InboxScreen(
                messages = vm.messages,
                busy = vm.busy,
                error = vm.error,
                onRefresh = { scope.launch { vm.refreshMessages() } },
                onOpen = { summary -> scope.launch { vm.openMessage(summary) } },
                modifier = Modifier.padding(padding)
            )

            2 -> AddressScreen(
                account = vm.account,
                remainingMillis = vm.remainingMillis,
                busy = vm.busy,
                onCopy = { copyText(context, vm.account?.address.orEmpty()) },
                onShare = { shareText(context, vm.account?.address.orEmpty()) },
                onCreate = { scope.launch { vm.createNewAccountSuspend() } },
                modifier = Modifier.padding(padding)
            )

            else -> MoreScreen(
                autoRefresh = vm.autoRefresh,
                onAutoRefreshChanged = vm::setAutoRefresh,
                onPrivacy = { showPrivacy = true }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    account: MailAccount?,
    messages: List<MailSummary>,
    busy: Boolean,
    error: String?,
    remainingMillis: Long,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06111D), Background)))
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Header(
                title = "بريد مؤقت",
                subtitle = "بسيط • سريع • بدون تسجيل"
            )
        }

        item {
            AddressCard(
                account = account,
                remainingMillis = remainingMillis,
                busy = busy,
                onCopy = onCopy,
                onShare = onShare,
                onCreate = onCreate
            )
        }

        if (!error.isNullOrBlank()) {
            item {
                ErrorCard(error = error)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Inbox,
                    title = "الوارد",
                    value = messages.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Default.Timer,
                    title = "المتبقي",
                    value = formatTime(remainingMillis),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionHeader(
                title = "أحدث الرسائل",
                action = "فتح الوارد",
                onAction = onOpenInbox
            )
        }

        if (messages.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Email,
                    title = "لا توجد رسائل بعد",
                    subtitle = "عندما تصل رسالة إلى عنوانك ستظهر هنا تلقائيًا."
                )
            }
        } else {
            items(messages.take(3), key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    onClick = onOpenInbox
                )
            }
        }

        item {
            InfoBanner()
        }
    }
}

@Composable
private fun AddressCard(
    account: MailAccount?,
    remainingMillis: Long,
    busy: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCreate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = SurfaceDark
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("عنوانك الحالي", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (account == null) "جاري إنشاء البريد..."
                        else "يعمل الآن",
                        color = if (account == null) Muted else Success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark2
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        account?.address ?: "سيظهر هنا عنوان البريد الحقيقي",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = White,
                            textDirection = TextDirection.Ltr
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = Cyan,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "ينتهي خلال ${formatTime(remainingMillis)}",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallActionButton(
                    text = "نسخ",
                    icon = Icons.Default.ContentCopy,
                    enabled = account != null && !busy,
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                )
                SmallActionButton(
                    text = "مشاركة",
                    icon = Icons.Default.Share,
                    enabled = account != null && !busy,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onCreate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("جارٍ التنفيذ")
                } else {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("إنشاء عنوان جديد")
                }
            }
        }
    }
}

@Composable
private fun InboxScreen(
    messages: List<MailSummary>,
    busy: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpen: (MailSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("صندوق الوارد", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${messages.count { !it.seen }} غير مقروءة",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onRefresh, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.size(21.dp),
                            strokeWidth = 2.dp,
                            color = Cyan
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) {
            item { ErrorCard(error) }
        }

        if (messages.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Inbox,
                    title = "الوارد فارغ",
                    subtitle = "لا توجد رسائل في العنوان الحالي. ستظهر الرسائل الجديدة هنا."
                )
            }
        } else {
            items(messages, key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    onClick = { onOpen(message) }
                )
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: MailSummary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (message.seen) SurfaceDark else SurfaceDark3
    ) {
        Row(
            Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D2746)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = Cyan
                )
            }

            Spacer(Modifier.size(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    message.senderName.ifBlank { message.senderAddress },
                    fontWeight = if (message.seen) FontWeight.Medium else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    message.subject,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    message.intro.ifBlank { "لا توجد معاينة للرسالة." },
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!message.seen) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Blue)
                )
            }
        }
    }
}

@Composable
private fun MessageDetailScreen(
    message: MailDetails,
    busy: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                }
                Text("الرسالة", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(48.dp))
            }
        }

        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceDark
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        message.subject,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message.senderName.ifBlank { message.senderAddress },
                        color = White,
                        fontSize = 13.sp
                    )
                    Text(
                        message.senderAddress,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = Muted,
                            textDirection = TextDirection.Ltr
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatDate(message.createdAt),
                        color = Muted,
                        fontSize = 11.sp
                    )

                    if (message.hasAttachments) {
                        Spacer(Modifier.height(10.dp))
                        StatusPill("توجد مرفقات", Cyan)
                    }
                }
            }
        }

        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF1F5FA)
            ) {
                Text(
                    messageBody(message),
                    Modifier.padding(18.dp),
                    color = Color(0xFF10151C),
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onDelete,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("حذف الرسالة")
            }
        }
    }
}

@Composable
private fun AddressScreen(
    account: MailAccount?,
    remainingMillis: Long,
    busy: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Header("العنوان الحالي", "إدارة البريد المؤقت")
        }

        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceDark
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("البريد النشط", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        account?.address ?: "لا يوجد عنوان نشط",
                        style = TextStyle(
                            fontSize = 17.sp,
                            color = White,
                            textDirection = TextDirection.Ltr
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    StatusPill(
                        text = if (account == null) "غير متصل" else "نشط • ${formatTime(remainingMillis)}",
                        color = if (account == null) Danger else Success
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallActionButton(
                            "نسخ",
                            Icons.Default.ContentCopy,
                            account != null && !busy,
                            onCopy,
                            Modifier.weight(1f)
                        )
                        SmallActionButton(
                            "مشاركة",
                            Icons.Default.Share,
                            account != null && !busy,
                            onShare,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onCreate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("إنشاء عنوان جديد")
            }
        }

        item {
            InfoCard(
                icon = Icons.Default.Shield,
                title = "بدون حساب شخصي",
                body = "التطبيق لا يطلب منك اسمًا أو رقم هاتف أو تسجيل دخول Google."
            )
        }
    }
}

@Composable
private fun MoreScreen(
    autoRefresh: Boolean,
    onAutoRefreshChanged: (Boolean) -> Unit,
    onPrivacy: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Header("المزيد", "إعدادات التطبيق ومعلوماته")
        }

        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceDark
            ) {
                Column {
                    SettingRow(
                        icon = Icons.Default.Refresh,
                        title = "التحديث التلقائي",
                        subtitle = "فحص البريد كل 20 ثانية أثناء الاستخدام",
                        trailing = {
                            Switch(
                                checked = autoRefresh,
                                onCheckedChange = onAutoRefreshChanged
                            )
                        }
                    )
                    HorizontalDivider(color = Color(0xFF142333))
                    SettingRow(
                        icon = Icons.Default.Timer,
                        title = "مدة العنوان",
                        subtitle = "60 دقيقة من لحظة الإنشاء"
                    )
                    HorizontalDivider(color = Color(0xFF142333))
                    SettingRow(
                        icon = Icons.Default.Shield,
                        title = "بدون تسجيل دخول",
                        subtitle = "لا يوجد حساب مستخدم داخل التطبيق"
                    )
                }
            }
        }

        item {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPrivacy),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceDark
            ) {
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "الخصوصية والاستخدام",
                    subtitle = "كيف يتعامل التطبيق مع عنوان البريد والرسائل"
                )
            }
        }

        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceDark
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("خدمة البريد", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "البريد المؤقت الفعلي يُنشأ ويُستقبل عبر Mail.tm. التطبيق يتصل بالخدمة عبر HTTPS ولا يتطلب حسابًا من المستخدم.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "الإسناد: mail.tm",
                        color = Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            openUrl(context, "https://mail.tm")
                        }
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("فتح موقع Mail.tm")
                    }
                }
            }
        }

        item {
            Text(
                "الإصدار 1.0.0",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color(0xFF63768A),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PrivacyScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                }
                Text("الخصوصية والاستخدام", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(48.dp))
            }
        }

        item {
            InfoCard(
                icon = Icons.Default.Shield,
                title = "لا يوجد حساب مستخدم",
                body = "لا نطلب منك تسجيل الدخول أو إنشاء حساب داخل التطبيق."
            )
        }

        item {
            InfoCard(
                icon = Icons.Default.Email,
                title = "البريد والرسائل",
                body = "العنوان المؤقت وبيانات الجلسة محفوظة محليًا على الجهاز بشكل مشفّر لتستمر الجلسة أثناء الاستخدام."
            )
        }

        item {
            InfoCard(
                icon = Icons.Default.Link,
                title = "الخدمة الخارجية",
                body = "إرسال واستقبال البريد يتم عبر Mail.tm وفق شروط واستخدام واجهة الخدمة. يجب الالتزام بالقوانين وشروط المواقع التي تستخدم معها البريد."
            )
        }

        item {
            InfoCard(
                icon = Icons.Default.Timer,
                title = "انتهاء العنوان",
                body = "التطبيق يتعامل مع العنوان على أنه مؤقت لمدة 60 دقيقة، ثم يحاول حذفه من الخدمة وينشئ عنوانًا جديدًا."
            )
        }

        item {
            InfoCard(
                icon = Icons.Default.Info,
                title = "سياسة الخصوصية الرسمية",
                body = "هذه الصفحة داخل التطبيق تلخّص التعامل مع البيانات. رابط السياسة المنشور على الويب يجب إضافته في بيانات المتجر قبل الإطلاق النهائي."
            )
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction) {
            Text(action, color = Cyan)
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceDark
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D2746)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Cyan)
            }
            Spacer(Modifier.size(9.dp))
            Column {
                Text(title, color = Muted, fontSize = 11.sp)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 11.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(6.dp))
        Text(text)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InfoBanner() {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0A1C2B)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.size(9.dp))
            Text(
                "استخدم العنوان المؤقت للخدمات التي تسمح بالبريد المؤقت. بعض المواقع قد ترفض عناوين البريد المؤقت.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2A1318)
    ) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(error, color = Color(0xFFFFB4B4), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D2746)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Cyan)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                subtitle,
                color = Muted,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceDark
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D2746)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Cyan)
            }
            Spacer(Modifier.size(11.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(body, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D2746)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Cyan)
        }

        Spacer(Modifier.size(11.dp))

        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        }

        trailing?.invoke()
    }
}

private fun messageBody(message: MailDetails): String {
    if (message.text.isNotBlank()) return message.text.trim()

    val html = message.html.joinToString("\n")
    if (html.isBlank()) return "محتوى الرسالة غير متوفر."

    return runCatching {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }.getOrDefault(html).ifBlank {
        "محتوى الرسالة غير متوفر."
    }
}

private fun friendlyError(t: Throwable): String {
    return when (t) {
        is MailTmException -> t.message ?: "تعذر إكمال الطلب."
        else -> "حدث خطأ غير متوقع. حاول مرة أخرى."
    }
}

private fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"

    val totalSeconds = milliseconds / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatDate(value: String): String {
    if (value.isBlank()) return "وقت غير معروف"

    return runCatching {
        val date = OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy  HH:mm"))
    }.getOrDefault(value)
}

private fun copyText(context: Context, value: String) {
    if (value.isBlank()) return

    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(
        ClipData.newPlainText("البريد المؤقت", value)
    )
    Toast.makeText(context, "تم نسخ العنوان", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, value: String) {
    if (value.isBlank()) return

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, value)
    }
    context.startActivity(
        Intent.createChooser(intent, "مشاركة البريد")
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
