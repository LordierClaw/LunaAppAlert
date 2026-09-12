package dev.lordierclaw.lunaappalert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lordierclaw.lunaappalert.core.*
import dev.lordierclaw.lunaappalert.ui.theme.*

@Composable
fun WelcomeScreen(onSetup: () -> Unit) {
    var how by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val largeHeight = maxHeight > 720.dp
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (largeHeight) 64.dp else 36.dp))
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                Surface(color = AlertSurface, shape = RoundedCornerShape(24.dp)) { Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { AppLogo(64.dp) } }
                Box(Modifier.align(Alignment.TopEnd).size(15.dp).background(AlertBlue, CircleShape))
            }
            Spacer(Modifier.height(28.dp))
            Text("App Alert", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
            Text("Nhận lời nhắc khi bạn mở hoặc tiếp tục sử dụng ứng dụng đã chọn.", Modifier.widthIn(max = 330.dp),
                style = MaterialTheme.typography.bodyLarge, color = AlertSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Bạn chọn ứng dụng và cách nhắc.", style = MaterialTheme.typography.bodyMedium, color = AlertMuted)
            Spacer(Modifier.height(42.dp))
            Column(Modifier.widthIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureRow(Icons.Outlined.NotificationsActive, "Lời nhắc đúng lúc", "Thông báo hoặc cảnh báo trên màn hình")
                FeatureRow(Icons.Outlined.Timer, "Thời điểm do bạn chọn", "Khi mở, dùng liên tục hoặc đủ thời gian")
                FeatureRow(Icons.Outlined.Lock, "100% trên thiết bị", "Không tài khoản. Không gửi dữ liệu đi.")
            }
            Spacer(Modifier.height(36.dp))
            PrimaryButton("Thiết lập App Alert", onSetup, Modifier.testTag("welcome_setup"), icon = Icons.AutoMirrored.Outlined.ArrowForward)
            TextButton(onClick = { how = true }, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)) { Text("Cách hoạt động", color = AlertSecondary) }
        }
    }
    if (how) AppAlertDialog(onDismissRequest = { how = false }, title = { Text("Cách App Alert hoạt động") },
        text = { Text("1. Chọn ứng dụng muốn nhận lời nhắc.\n\n2. Đặt quy tắc riêng hoặc dùng chung trong một nhóm.\n\n3. Chọn thông báo hoặc cảnh báo trên màn hình. Bạn luôn có thể tiếp tục sử dụng ứng dụng.") },
        confirmButton = { TextButton(onClick = { how = false }) { Text("Đã hiểu") } })
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Surface(color = AlertSurface, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, Modifier.size(34.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
            }
        }
    }
}

@Composable
fun AccessScreen(vm: AppViewModel, onBack: () -> Unit, openAccess: (AccessAction) -> Unit, onContinue: () -> Unit) {
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    AppScreen("Quyền truy cập", onBack, bottom = { Footer { PrimaryButton("Tiếp tục", onContinue, Modifier.testTag("access_continue")) } }) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Để lời nhắc hoạt động", style = MaterialTheme.typography.headlineSmall) }
            item { Text("App Alert cần quyền truy cập sử dụng để nhận biết ứng dụng. Các quyền khác chỉ cần cho kiểu cảnh báo bạn chọn.", color = AlertSecondary, style = MaterialTheme.typography.bodyMedium) }
            item { SectionHeader("Quyền cần thiết") }
            item { AccessRow("Truy cập sử dụng ứng dụng", "Nhận biết khi ứng dụng đã chọn đang được sử dụng.", Icons.Outlined.QueryStats,
                permissions.usage, "grant_usage", required = true) { openAccess(AccessAction.USAGE) } }
            item { SectionHeader("Cách nhận cảnh báo") }
            item { AccessRow("Hiển thị trên ứng dụng khác", "Dùng cho cảnh báo xuất hiện trên màn hình.", Icons.Outlined.Layers,
                permissions.overlay, "grant_overlay") { openAccess(AccessAction.OVERLAY) } }
            item { AccessRow("Thông báo", "Dùng cho lời nhắc trong bảng thông báo.", Icons.Outlined.NotificationsNone,
                permissions.notifications, "grant_notifications") { openAccess(AccessAction.NOTIFICATIONS) } }
            item { SectionHeader("Độ ổn định") }
            item { SettingsRow(Icons.Outlined.BatterySaver, "Hoạt động trong nền", "Một số điện thoại có thể giới hạn việc theo dõi.", { openAccess(AccessAction.BATTERY) }) }
            item { Text("Bạn có thể thiết lập ứng dụng và quy tắc trước, rồi cấp quyền sau.", style = MaterialTheme.typography.bodySmall, color = AlertMuted) }
        }
    }
}

@Composable
private fun AccessRow(title: String, subtitle: String, icon: ImageVector, granted: Boolean, tag: String,
    required: Boolean = false, onClick: () -> Unit) {
    Surface(color = AlertSurface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(icon)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp)); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(if (granted) "Đã bật" else if (required) "Cần cấp quyền" else "Tùy theo quy tắc",
                    color = if (granted) AlertSuccess else if (required) AlertWarning else AlertSecondary,
                    background = Color.White)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Text(if (granted) "Quản lý" else "Cấp quyền") }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, onSettings: () -> Unit, onAccess: () -> Unit, onApp: (String) -> Unit,
    onGroup: (String) -> Unit, onAdd: () -> Unit, onCreate: () -> Unit) {
    val config by vm.configuration.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val monitor by vm.monitorState.collectAsStateWithLifecycle()
    val catalog by vm.installedApps.collectAsStateWithLifecycle()
    val installation = remember(config.apps, catalog) { config.apps.associate { it.packageName to vm.isInstalled(it.packageName) } }
    var addMenu by remember { mutableStateOf(false) }
    fun summary(app: TrackedApp): String = RuleResolver.resolve(app, config, permissions, installation[app.packageName] == true, settings.monitoringEnabled).effectiveSummary()
    val needsDelivery = config.apps.any { app -> RuleResolver.resolve(app, config, permissions, installation[app.packageName] == true, settings.monitoringEnabled).any { it.status == RuleStatus.NEEDS_PERMISSION } }
    AppScreen("App Alert", actions = {
        IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) { Icon(Icons.Outlined.Settings, "Cài đặt") }
    }, floatingAction = {
        Box {
            FloatingActionButton(onClick = { addMenu = true }, modifier = Modifier.testTag("home_add"), shape = RoundedCornerShape(16.dp),
                containerColor = AlertBlue, contentColor = Color.White) { Icon(Icons.Outlined.Add, "Thêm") }
            DropdownMenu(addMenu, { addMenu = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                DropdownMenuItem(text = { Text("Thêm ứng dụng") }, modifier = Modifier.testTag("menu_add_app"),
                    leadingIcon = { Icon(Icons.Outlined.Apps, null) }, onClick = { addMenu = false; onAdd() })
                DropdownMenuItem(text = { Text("Tạo nhóm") }, modifier = Modifier.testTag("menu_create_group"),
                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { addMenu = false; onCreate() })
            }
        }
    }) { padding ->
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!permissions.usage || !settings.monitoringEnabled || !monitor.running || needsDelivery) item {
                InfoCard(if (!settings.monitoringEnabled) "Theo dõi đã tạm dừng" else "Theo dõi cần được kiểm tra",
                    when { !permissions.usage -> "Bật quyền truy cập sử dụng để các quy tắc hoạt động."
                        !settings.monitoringEnabled -> "Các ứng dụng và quy tắc của bạn vẫn được lưu."
                        needsDelivery -> "Một số quy tắc đang chờ quyền hiển thị cảnh báo."
                        else -> monitor.message }, warning = true, action = if (!settings.monitoringEnabled) "Bật theo dõi" else "Kiểm tra quyền",
                    onAction = { if (!settings.monitoringEnabled) vm.setMonitoring(true) else onAccess() })
            }
            if (config.apps.isEmpty() && config.groups.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconTile(Icons.Outlined.Apps, Modifier.size(72.dp), background = AlertBlueSoft)
                    Spacer(Modifier.height(24.dp)); Text("Chưa có ứng dụng", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(10.dp)); Text("Thêm ứng dụng, rồi chọn lời nhắc riêng hoặc dùng chung trong một nhóm.",
                        Modifier.widthIn(max = 320.dp), textAlign = TextAlign.Center, color = AlertSecondary)
                    Spacer(Modifier.height(28.dp)); PrimaryButton("Thêm ứng dụng", onAdd, Modifier.testTag("home_add_empty"), icon = Icons.Outlined.Add)
                    TextButton(onClick = onCreate, modifier = Modifier.testTag("create_group")) { Text("Tạo nhóm") }
                }
            }
            items(config.groups.sortedBy { it.sortOrder }, key = { "group_${it.id}" }) { group ->
                val apps = config.apps.filter { it.groupId == group.id }.sortedBy { it.sortOrder }
                Surface(color = AlertSurface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onGroup(group.id) }.testTag("group_${group.id}"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconTile(Icons.Outlined.Folder, background = Color(0xFFE1E9FF))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(17.dp), tint = AlertSecondary)
                                }
                                Text("${config.rules.count { it.ownerType == OwnerType.GROUP && it.ownerId == group.id }} quy tắc · ${apps.size} ứng dụng",
                                    style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
                            }
                            AppToggle(group.enabled, { vm.toggleGroup(group, it) }, "Bật nhóm ${group.name}", "group_toggle_${group.id}")
                        }
                        apps.forEach { app -> AppRow(app, vm, summary(app), { onApp(app.id) }, background = Color.White) }
                        if (apps.isEmpty()) Text("Chưa có ứng dụng trong nhóm", Modifier.padding(10.dp), color = AlertMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val ungrouped = config.apps.filter { it.groupId == null }.sortedBy { it.sortOrder }
            if (ungrouped.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Chưa phân nhóm", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${ungrouped.size} ứng dụng", style = MaterialTheme.typography.bodySmall, color = AlertMuted)
                }
            }
            items(ungrouped, key = { it.id }) { AppRow(it, vm, summary(it), { onApp(it.id) }) }
        }
    }
}

@Composable
fun GroupScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onEdit: () -> Unit, onAddApps: () -> Unit,
    onApp: (String) -> Unit, onRule: (String?) -> Unit) {
    val config by vm.configuration.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val group = config.groups.firstOrNull { it.id == id }
    val catalog by vm.installedApps.collectAsStateWithLifecycle()
    val installation = remember(config.apps, catalog) { config.apps.associate { it.packageName to vm.isInstalled(it.packageName) } }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    AppScreen("Chi tiết nhóm", onBack, actions = { Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Tùy chọn nhóm") }
        DropdownMenu(menu, { menu = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            DropdownMenuItem(text = { Text("Chỉnh sửa nhóm") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("Xóa nhóm", color = AlertDanger) }, onClick = { menu = false; confirmDelete = true })
        }
    } }) { padding ->
        if (group != null) {
            val apps = config.apps.filter { it.groupId == id }
            val rules = config.rules.filter { it.ownerType == OwnerType.GROUP && it.ownerId == id }
            LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Surface(color = AlertSurface, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(Icons.Outlined.Folder, background = Color(0xFFE1E9FF))
                        Column(Modifier.weight(1f)) { Text(group.name, style = MaterialTheme.typography.titleMedium); Text("${apps.size} ứng dụng · ${rules.size} quy tắc", style = MaterialTheme.typography.bodySmall, color = AlertSecondary) }
                        AppToggle(group.enabled, { vm.toggleGroup(group, it) }, "Bật nhóm ${group.name}", "group_toggle_${group.id}")
                    }
                } }
                if (!group.enabled) item { InfoCard("Nhóm đang tạm dừng", "Trạng thái riêng của các ứng dụng và quy tắc được giữ nguyên.", warning = true) }
                item { Spacer(Modifier.height(8.dp)); SectionHeader("Ứng dụng trong nhóm", "Chạm vào ứng dụng để xem quy tắc riêng.") }
                items(apps, key = { it.id }) { app -> AppRow(app, vm, RuleResolver.resolve(app, config, permissions, installation[app.packageName] == true, settings.monitoringEnabled).effectiveSummary(), { onApp(app.id) }) }
                if (apps.isEmpty()) item { Text("Nhóm này chưa có ứng dụng.", style = MaterialTheme.typography.bodyMedium, color = AlertSecondary) }
                item { SoftButton("Thêm ứng dụng vào nhóm", onAddApps, Modifier.testTag("group_add_app")) }
                item { Spacer(Modifier.height(12.dp)); SectionHeader("Quy tắc nhóm", "Áp dụng riêng cho từng ứng dụng trong ${group.name}.", "Thêm", { onRule(null) }, "add_group_rule") }
                if (rules.isEmpty()) item { EmptyRules { onRule(null) } }
                items(rules, key = { it.id }) { rule ->
                    val overrides = apps.count { app -> config.rules.any { it.ownerType == OwnerType.APP && it.ownerId == app.id && it.enabled && it.signature() == rule.signature() } }
                    RuleCard(rule, status = when { !rule.enabled -> RuleStatus.OFF; !group.enabled -> RuleStatus.PAUSED_BY_GROUP; !settings.monitoringEnabled -> RuleStatus.MONITORING_PAUSED
                        !permissions.usage || rule.alertType == AlertType.OVERLAY && !permissions.overlay || rule.alertType == AlertType.NOTIFICATION && !permissions.notifications -> RuleStatus.NEEDS_PERMISSION; else -> RuleStatus.ACTIVE },
                        supporting = "Áp dụng cho ${apps.size} ứng dụng" + if (overrides > 0) " · $overrides ứng dụng có quy tắc thay thế" else "",
                        onEdit = { onRule(rule.id) }, onToggle = { vm.toggleRule(rule, it) }, onDelete = { vm.deleteRule(rule) })
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
    if (confirmDelete && group != null) AppAlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Xóa ${group.name}?") },
        text = { Text("Quy tắc của nhóm sẽ bị xóa. Các ứng dụng chuyển sang Chưa phân nhóm và giữ nguyên quy tắc riêng.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteGroup(group, onBack) }, modifier = Modifier.testTag("confirm_delete_group")) { Text("Xóa nhóm", color = AlertDanger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } })
}

@Composable
fun AppDetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onGroup: (String) -> Unit, onRule: (String?) -> Unit) {
    val config by vm.configuration.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val app = config.apps.firstOrNull { it.id == id }
    val catalog by vm.installedApps.collectAsStateWithLifecycle()
    val installed = remember(app?.packageName, catalog) { app?.let { vm.isInstalled(it.packageName) } ?: false }
    var menu by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    AppScreen("Chi tiết ứng dụng", onBack, actions = { Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("app_menu")) { Icon(Icons.Outlined.MoreVert, "Tùy chọn ứng dụng") }
        DropdownMenu(menu, { menu = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            DropdownMenuItem(text = { Text("Chuyển nhóm") }, modifier = Modifier.testTag("move_app"), onClick = { menu = false; move = true })
            DropdownMenuItem(text = { Text("Bỏ ứng dụng", color = AlertDanger) }, modifier = Modifier.testTag("remove_app"), onClick = { menu = false; remove = true })
        }
    } }) { padding ->
        if (app != null) {
            val group = config.groups.firstOrNull { it.id == app.groupId }
            val resolved = RuleResolver.resolve(app, config, permissions, installed, settings.monitoringEnabled)
            val own = resolved.filter { it.rule.ownerType == OwnerType.APP }
            LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppIcon(app.packageName, vm, 56.dp)
                    Column(Modifier.weight(1f)) { Text(app.displayName, style = MaterialTheme.typography.titleLarge); Text(group?.name ?: "Chưa phân nhóm", color = AlertSecondary, style = MaterialTheme.typography.bodyMedium) }
                    AppToggle(app.enabled, { vm.toggleApp(app, it) }, "Theo dõi ${app.displayName}", "app_toggle_${app.id}")
                } }
                if (!installed) item { InfoCard("Ứng dụng chưa được cài đặt", "Quy tắc vẫn được giữ lại và hoạt động khi ứng dụng được cài lại.", warning = true) }
                else if (group?.enabled == false || !app.enabled) item { InfoCard("Ứng dụng đang tạm dừng", if (group?.enabled == false) "Bật lại nhóm ${group.name} để tiếp tục theo dõi." else "Bật theo dõi ứng dụng để nhận lời nhắc.", warning = true) }
                if (group != null) {
                    item { Spacer(Modifier.height(8.dp)); SectionHeader("Từ ${group.name}", "Áp dụng tự động. Quy tắc riêng được ưu tiên khi trùng khớp.") }
                    val inherited = resolved.filter { it.rule.ownerType == OwnerType.GROUP }
                    if (inherited.isEmpty()) item { Text("Nhóm chưa có quy tắc.", color = AlertSecondary, style = MaterialTheme.typography.bodyMedium) }
                    items(inherited, key = { it.rule.id }) { resolvedRule -> RuleCard(resolvedRule.rule, resolvedRule.status,
                        supporting = "Từ ${group.name}", editable = false, onEdit = { onGroup(group.id) }) }
                    item { TextButton(onClick = { onGroup(group.id) }) { Text("Xem nhóm"); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) } }
                }
                item { Spacer(Modifier.height(4.dp)); SectionHeader("Quy tắc ứng dụng", "Chỉ áp dụng cho ${app.displayName}.", "Thêm", { onRule(null) }, "add_app_rule") }
                if (own.isEmpty()) item { EmptyRules { onRule(null) } }
                items(own, key = { it.rule.id }) { resolvedRule ->
                    val overrides = resolved.any { it.rule.ownerType == OwnerType.GROUP && it.status == RuleStatus.OVERRIDDEN && it.rule.signature() == resolvedRule.rule.signature() }
                    RuleCard(resolvedRule.rule, resolvedRule.status, if (overrides) "Ưu tiên thay cho quy tắc nhóm trùng khớp" else null,
                        onEdit = { onRule(resolvedRule.rule.id) }, onToggle = { vm.toggleRule(resolvedRule.rule, it) }, onDelete = { vm.deleteRule(resolvedRule.rule) })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
    if (move && app != null) MoveAppDialog(app, config, onDismiss = { move = false }) { target -> vm.moveApp(app, target); move = false }
    if (remove && app != null) AppAlertDialog(onDismissRequest = { remove = false }, title = { Text("Bỏ ${app.displayName}?") },
        text = { Text("Ứng dụng và các quy tắc riêng sẽ được bỏ khỏi App Alert. Ứng dụng trên điện thoại không bị gỡ cài đặt.") },
        confirmButton = { TextButton(onClick = { remove = false; vm.deleteApp(app, onBack) }, modifier = Modifier.testTag("confirm_remove_app")) { Text("Bỏ ứng dụng", color = AlertDanger) } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text("Hủy") } })
}

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, openAccess: (AccessAction) -> Unit) {
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val monitor by vm.monitorState.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<String?>(null) }
    AppScreen("Cài đặt", onBack) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionHeader("Theo dõi") }
            item { Surface(color = AlertSurface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.NotificationsActive)
                    Column(Modifier.weight(1f)) { Text("Theo dõi ứng dụng", style = MaterialTheme.typography.titleMedium); Text(monitor.message, color = if (monitor.running) AlertSuccess else AlertSecondary, style = MaterialTheme.typography.bodySmall) }
                    AppToggle(settings.monitoringEnabled, vm::setMonitoring, "Bật theo dõi ứng dụng", "monitoring_toggle")
                }
            } }
            item { Spacer(Modifier.height(8.dp)); SectionHeader("Quyền hệ thống") }
            item { AccessRow("Truy cập sử dụng ứng dụng", "Quyền cần thiết để theo dõi.", Icons.Outlined.QueryStats, permissions.usage, "grant_usage", true) { openAccess(AccessAction.USAGE) } }
            item { AccessRow("Hiển thị trên ứng dụng khác", "Cảnh báo khi đang dùng ứng dụng.", Icons.Outlined.Layers, permissions.overlay, "grant_overlay") { openAccess(AccessAction.OVERLAY) } }
            item { AccessRow("Thông báo", "Lời nhắc trong bảng thông báo.", Icons.Outlined.NotificationsNone, permissions.notifications, "grant_notifications") { openAccess(AccessAction.NOTIFICATIONS) } }
            item { SettingsRow(Icons.Outlined.BatterySaver, "Hoạt động trong nền", "Hướng dẫn tăng độ ổn định", { openAccess(AccessAction.BATTERY) }) }
            item { Spacer(Modifier.height(8.dp)); SectionHeader("Về App Alert") }
            item { SettingsRow(Icons.Outlined.Lock, "Quyền riêng tư", "Dữ liệu chỉ được lưu trên thiết bị", { info = "privacy" }) }
            item { SettingsRow(Icons.Outlined.Description, "Giấy phép nguồn mở", "AndroidX, Kotlin và Inter", { info = "licenses" }) }
            item { Text("App Alert · Phiên bản 1.0\nĐược thiết kế để nhắc, quyền lựa chọn luôn là của bạn.", Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = AlertMuted) }
        }
    }
    if (info != null) AppAlertDialog(onDismissRequest = { info = null }, title = { Text(if (info == "privacy") "Quyền riêng tư" else "Giấy phép nguồn mở") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(if (info == "privacy") "App Alert hoạt động hoàn toàn trên điện thoại. Không có tài khoản, quảng cáo, dịch vụ phân tích hay đồng bộ đám mây.\n\nỨng dụng đọc sự kiện sử dụng để đánh giá các quy tắc bạn chọn. Chỉ cấu hình và trạng thái tối thiểu của ngày hiện tại được giữ lại; không tạo lịch sử hoạt động.\n\nBạn có thể tắt theo dõi, thu hồi quyền hoặc xóa dữ liệu trong Cài đặt Android bất cứ lúc nào."
            else "AndroidX và Kotlin: Apache License 2.0.\nhttps://www.apache.org/licenses/LICENSE-2.0\n\nInter: Copyright 2020 The Inter Project Authors. SIL Open Font License 1.1.\nhttps://openfontlicense.org\n\nBạn được phép sử dụng, nghiên cứu, sửa đổi và phân phối phông chữ theo giấy phép SIL OFL. Bản quyền và giấy phép đầy đủ được kèm trong mã nguồn dự án.")
        } }, confirmButton = { TextButton(onClick = { info = null }) { Text("Đóng") } })
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(color = AlertSurface, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(subtitle, color = AlertSecondary, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AlertMuted)
        }
    }
}
