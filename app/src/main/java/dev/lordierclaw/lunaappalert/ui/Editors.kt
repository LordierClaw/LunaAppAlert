package dev.lordierclaw.lunaappalert.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lordierclaw.lunaappalert.core.*
import dev.lordierclaw.lunaappalert.ui.theme.*
import java.text.Normalizer
import java.util.UUID

private fun String.searchKey() = Normalizer.normalize(lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")

@Composable
fun AppPickerScreen(vm: AppViewModel, groupId: String?, onBack: () -> Unit, onDone: () -> Unit) {
    val installed by vm.installedApps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    val config by vm.configuration.collectAsStateWithLifecycle()
    val group = config.groups.firstOrNull { it.id == groupId }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var confirmMove by remember { mutableStateOf(false) }
    val compact = compactEditor()
    fun save() { saving = true; vm.addApps(selected.toSet(), groupId) { onDone() }.invokeOnCompletion { saving = false } }
    val moving = config.apps.filter { it.packageName in selected && it.groupId != groupId }
    fun submit() { if (moving.isNotEmpty() && group != null) confirmMove = true else save() }
    AppScreen(if (group == null) "Thêm ứng dụng" else "Thêm vào ${group.name}", onBack,
        actions = { if (compact) EditorSaveAction(selected.isNotEmpty() && !saving, "picker_save", ::submit) },
        bottom = { if (!compact) Footer { PrimaryButton(if (selected.isEmpty()) "Chọn ứng dụng" else "Thêm (${selected.size})",
            ::submit,
            Modifier.testTag("picker_save"), enabled = selected.isNotEmpty() && !saving, icon = Icons.Outlined.Check) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("app_search"), placeholder = { Text("Tìm ứng dụng") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Xóa tìm kiếm") } },
                singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
            Text("Chọn các ứng dụng bạn muốn nhận lời nhắc.", Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                val results = installed.filter { it.label.searchKey().contains(query.searchKey()) || it.packageName.contains(query, true) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (results.isEmpty()) item { Text("Không tìm thấy ứng dụng.", Modifier.padding(20.dp), color = AlertSecondary) }
                    items(results, key = { it.packageName }) { entry ->
                        val existing = config.apps.firstOrNull { it.packageName == entry.packageName }
                        val alreadyHere = existing != null && (groupId == null || existing.groupId == groupId)
                        val checked = entry.packageName in selected || alreadyHere
                        val toggle = { if (!alreadyHere) selected = if (entry.packageName in selected) selected - entry.packageName else selected + entry.packageName }
                        Surface(color = AlertSurface, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().clickable(enabled = !alreadyHere, onClick = toggle).testTag("picker_app_${entry.packageName}").padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppIcon(entry.packageName, vm)
                                Column(Modifier.weight(1f)) {
                                    Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(if (alreadyHere) "Đã thêm" else if (existing != null) config.groups.firstOrNull { it.id == existing.groupId }?.name ?: "Chưa phân nhóm" else "Ứng dụng đã cài đặt",
                                        style = MaterialTheme.typography.bodySmall, color = AlertSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Checkbox(checked, { toggle() }, enabled = !alreadyHere)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmMove && group != null) AppAlertDialog(onDismissRequest = { confirmMove = false }, title = { Text("Chuyển ${moving.size} ứng dụng?") },
        text = { Text("Các ứng dụng đã chọn sẽ chuyển vào ${group.name} và nhận quy tắc của nhóm này. Quy tắc riêng của từng ứng dụng được giữ nguyên.") },
        confirmButton = { TextButton(onClick = { confirmMove = false; save() }, modifier = Modifier.testTag("confirm_move")) { Text("Chuyển và thêm") } },
        dismissButton = { TextButton(onClick = { confirmMove = false }) { Text("Hủy") } })
}

@Composable
fun GroupEditorScreen(vm: AppViewModel, id: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val config by vm.configuration.collectAsStateWithLifecycle()
    val existing = config.groups.firstOrNull { it.id == id }
    var name by rememberSaveable(id) { mutableStateOf(existing?.name ?: "") }
    var selected by rememberSaveable(id) { mutableStateOf(config.apps.filter { id != null && it.groupId == id }.map { it.id }) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val compact = compactEditor()
    val originalSelection = config.apps.filter { id != null && it.groupId == id }.map { it.id }.toSet()
    val dirty = name != (existing?.name ?: "") || selected.toSet() != originalSelection
    fun back() { if (dirty) discard = true else onBack() }
    fun save() { saving = true; vm.saveGroup(existing, name, selected.toSet()) { onDone(it) }.invokeOnCompletion { saving = false } }
    fun submit() { if (config.apps.any { it.id in selected && it.groupId != null && it.groupId != id }) confirm = true else save() }
    BackHandler { back() }
    AppScreen(if (existing == null) "Tạo nhóm" else "Chỉnh sửa nhóm", ::back,
        actions = { if (compact) EditorSaveAction(name.isNotBlank() && !saving, "group_save", ::submit) },
        bottom = { if (!compact) Footer { PrimaryButton("Lưu nhóm", ::submit,
            Modifier.testTag("group_save"), enabled = name.isNotBlank() && !saving, icon = Icons.Outlined.Check) } }) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth().testTag("group_name"), label = { Text("Tên nhóm") },
                placeholder = { Text("Ví dụ: Mạng xã hội") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()) }
            item { SectionHeader("Ứng dụng", "Chọn ứng dụng đã thêm vào App Alert để xếp vào nhóm.") }
            if (config.apps.isEmpty()) item { InfoCard("Nhóm có thể bắt đầu trống", "Sau khi lưu nhóm, bạn có thể thêm ứng dụng và tạo quy tắc chung.", Icons.Outlined.FolderOpen) }
            items(config.apps, key = { it.id }) { app ->
                val toggle = { selected = if (app.id in selected) selected - app.id else selected + app.id }
                Surface(color = AlertSurface, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = toggle).testTag("group_select_${app.id}").padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app.packageName, vm)
                        Column(Modifier.weight(1f)) { Text(app.displayName, style = MaterialTheme.typography.titleSmall); Text(config.groups.firstOrNull { it.id == app.groupId }?.name ?: "Chưa phân nhóm", style = MaterialTheme.typography.bodySmall, color = AlertSecondary) }
                        Checkbox(app.id in selected, { toggle() })
                    }
                }
            }
            item { Text("Ứng dụng trong nhóm nhận các quy tắc chung. Quy tắc riêng của mỗi ứng dụng vẫn được giữ nguyên.", color = AlertSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (confirm) AppAlertDialog(onDismissRequest = { confirm = false }, title = { Text("Thay đổi nhóm của ứng dụng?") },
        text = { Text("Các ứng dụng đã chọn sẽ rời nhóm cũ và nhận quy tắc của “${name.trim()}”. Các quy tắc riêng không thay đổi.") },
        confirmButton = { TextButton(onClick = { confirm = false; save() }, modifier = Modifier.testTag("confirm_move")) { Text("Lưu thay đổi") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Hủy") } })
    if (discard) DiscardDialog({ discard = false }, onBack)
}

@Composable
fun MoveAppDialog(app: TrackedApp, config: Configuration, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    var target by rememberSaveable(app.id) { mutableStateOf(app.groupId) }
    val rules = config.rules.filter { it.ownerType == OwnerType.GROUP && it.ownerId == target && it.enabled }
    val own = config.rules.filter { it.ownerType == OwnerType.APP && it.ownerId == app.id && it.enabled }
    val overridden = rules.count { rule -> own.any { it.signature() == rule.signature() } }
    AppAlertDialog(onDismissRequest = onDismiss, title = { Text("Chuyển ${app.displayName}") },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().clickable { target = null }.testTag("move_target_ungrouped"), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(target == null, { target = null }); Text("Chưa phân nhóm")
            }
            config.groups.forEach { group -> Row(Modifier.fillMaxWidth().clickable { target = group.id }.testTag("move_target_${group.id}"), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(target == group.id, { target = group.id }); Text(group.name)
            } }
            Spacer(Modifier.height(12.dp))
            Text(if (target == null) "Ứng dụng sẽ chỉ dùng các quy tắc riêng."
                else "${rules.size} quy tắc nhóm sẽ áp dụng. $overridden quy tắc trùng khớp được quy tắc riêng thay thế.", color = AlertSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp)); Text("Quy tắc riêng của ${app.displayName} được giữ nguyên.", style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
        } }, confirmButton = { TextButton(onClick = { onMove(target) }, enabled = target != app.groupId, modifier = Modifier.testTag("confirm_move")) { Text("Chuyển") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } })
}

@Composable
fun RuleEditorScreen(vm: AppViewModel, ownerType: OwnerType, ownerId: String, ruleId: String?, onBack: () -> Unit,
    onSaved: () -> Unit, onExisting: (String) -> Unit, openAccess: (AccessAction) -> Unit) {
    val config by vm.configuration.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val existing = config.rules.firstOrNull { it.id == ruleId }
    val groupOwner = config.groups.firstOrNull { it.id == ownerId }
    val appOwner = config.apps.firstOrNull { it.id == ownerId }
    val ownerName = if (ownerType == OwnerType.GROUP) groupOwner?.name ?: "nhóm" else appOwner?.displayName ?: "ứng dụng"
    val parent = config.groups.firstOrNull { it.id == appOwner?.groupId }
    val newId = rememberSaveable(ownerType, ownerId, ruleId) { ruleId ?: UUID.randomUUID().toString() }
    var trigger by rememberSaveable(newId) { mutableStateOf(existing?.triggerType ?: TriggerType.CONTINUOUS_USE) }
    var threshold by rememberSaveable(newId) { mutableStateOf((existing?.thresholdMinutes ?: 20).toString()) }
    var repeat by rememberSaveable(newId) { mutableStateOf(existing?.repeatEnabled ?: false) }
    var interval by rememberSaveable(newId) { mutableStateOf((existing?.repeatEveryMinutes ?: 5).toString()) }
    var count by rememberSaveable(newId) { mutableStateOf((existing?.repeatMaxCount ?: 3).toString()) }
    var alert by rememberSaveable(newId) { mutableStateOf(existing?.alertType ?: AlertType.OVERLAY) }
    var message by rememberSaveable(newId) { mutableStateOf(existing?.customMessage ?: "") }
    var attempted by remember { mutableStateOf(false) }
    val compact = compactEditor()
    var saving by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val draft = Rule(newId, ownerType, ownerId, existing?.enabled ?: true, trigger, threshold.toIntOrNull(), repeat,
        interval.toIntOrNull(), count.toIntOrNull(), alert, message).normalized()
    val baseline = existing?.normalized() ?: Rule(newId, ownerType, ownerId).normalized()
    val dirty = draft != baseline
    val errors = RuleValidator.validate(draft, config.rules)
    val duplicate = RuleValidator.matchingRule(draft, config.rules)
    val inherited = config.rules.firstOrNull { ownerType == OwnerType.APP && draft.enabled && it.enabled &&
        it.ownerType == OwnerType.GROUP && it.ownerId == appOwner?.groupId && it.signature() == draft.signature() }
    fun back() { if (dirty) discard = true else onBack() }
    fun submit() {
        attempted = true
        if (errors.isEmpty()) { saving = true; vm.saveRule(draft) { onSaved() }.invokeOnCompletion { saving = false } }
    }
    BackHandler { back() }
    AppScreen(if (existing == null) if (ownerType == OwnerType.GROUP) "Thêm quy tắc nhóm" else "Thêm quy tắc ứng dụng" else "Chỉnh sửa quy tắc", ::back,
        actions = { if (compact) EditorSaveAction(!saving, "rule_save", ::submit) },
        bottom = { if (!compact) Footer {
            PrimaryButton(if (saving) "Đang lưu…" else "Lưu quy tắc", ::submit, Modifier.testTag("rule_save"), enabled = !saving, icon = Icons.Outlined.Check)
            TextButton(onClick = ::back, modifier = Modifier.fillMaxWidth()) { Text("Hủy", color = AlertSecondary) }
        } }) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(if (ownerType == OwnerType.GROUP) "Áp dụng cho từng ứng dụng trong $ownerName. Quy tắc riêng được ưu tiên khi trùng khớp."
                else "Lời nhắc dành cho $ownerName." + if (parent != null) " Được ưu tiên trước quy tắc trùng khớp từ ${parent.name}." else "",
                style = MaterialTheme.typography.bodySmall, color = AlertSecondary) }
            item { SectionHeader("Khi nào nhắc bạn?") }
            item { ChoiceCard(trigger == TriggerType.ON_LAUNCH, "Khi mở ứng dụng", "Nhắc mỗi khi bắt đầu một lần sử dụng mới.", Icons.Outlined.OpenInNew,
                "trigger_ON_LAUNCH") { trigger = TriggerType.ON_LAUNCH } }
            item { ChoiceCard(trigger == TriggerType.CONTINUOUS_USE, "Sau khi dùng liên tục", "Nhắc sau một khoảng thời gian sử dụng liền mạch.", Icons.Outlined.Timer,
                "trigger_CONTINUOUS_USE") { trigger = TriggerType.CONTINUOUS_USE } }
            item { ChoiceCard(trigger == TriggerType.DAILY_TOTAL, "Đủ thời gian trong ngày", "Nhắc khi tổng thời gian hôm nay đạt mức đã đặt.", Icons.Outlined.Today,
                "trigger_DAILY_TOTAL") { trigger = TriggerType.DAILY_TOTAL } }
            if (trigger != TriggerType.ON_LAUNCH) {
                item { Spacer(Modifier.height(4.dp)); SectionHeader(if (trigger == TriggerType.CONTINUOUS_USE) "Thời gian ban đầu" else "Thời gian mỗi ngày") }
                item { NumberField(threshold, { threshold = it }, "Thời gian", "phút", "rule_threshold",
                    errors.firstOrNull { it.field == "thresholdMinutes" }?.message.takeIf { attempted }) }
            }
            if (trigger == TriggerType.DAILY_TOTAL && ownerType == OwnerType.GROUP) item {
                InfoCard("Tính riêng cho từng ứng dụng", "Mỗi ứng dụng được nhắc khi đạt $threshold phút hôm nay. Thời gian của các ứng dụng không cộng gộp.")
            }
            if (trigger == TriggerType.CONTINUOUS_USE) {
                item { Surface(color = AlertSurface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Text("Lặp lại khi vẫn đang sử dụng", style = MaterialTheme.typography.titleSmall); Text("Chỉ trong cùng một lần sử dụng liên tục.", style = MaterialTheme.typography.bodySmall, color = AlertSecondary) }
                            AppToggle(repeat, { repeat = it }, "Lặp lại cảnh báo", "rule_repeat")
                        }
                        if (repeat) {
                            NumberField(interval, { interval = it }, "Lặp sau mỗi", "phút", "rule_repeat_interval", errors.firstOrNull { it.field == "repeatEveryMinutes" }?.message.takeIf { attempted })
                            NumberField(count, { count = it }, "Dừng sau", "lần lặp", "rule_repeat_count", errors.firstOrNull { it.field == "repeatMaxCount" }?.message.takeIf { attempted })
                            if (draft.repeatPreview().isNotBlank()) Text(draft.repeatPreview(), style = MaterialTheme.typography.bodySmall, color = AlertBlue)
                        }
                    }
                } }
            }
            item { Spacer(Modifier.height(6.dp)); SectionHeader("Cách nhận cảnh báo", "Chọn cách App Alert xuất hiện trên thiết bị.") }
            item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceCard(alert == AlertType.NOTIFICATION, "Thông báo", "Một lời nhắc trong bảng thông báo.", Icons.Outlined.NotificationsNone, "alert_NOTIFICATION") { alert = AlertType.NOTIFICATION }
                ChoiceCard(alert == AlertType.OVERLAY, "Cảnh báo trên màn hình", "Hiển thị phía trên ứng dụng đang dùng.", Icons.Outlined.Layers, "alert_OVERLAY") { alert = AlertType.OVERLAY }
            } }
            if (alert == AlertType.OVERLAY && !permissions.overlay || alert == AlertType.NOTIFICATION && !permissions.notifications) item {
                InfoCard("Quy tắc cần quyền để hoạt động", "Bạn vẫn có thể lưu quy tắc và cấp quyền sau.", warning = true,
                    action = "Cấp quyền", onAction = { openAccess(if (alert == AlertType.OVERLAY) AccessAction.OVERLAY else AccessAction.NOTIFICATIONS) })
            }
            item { SectionHeader("Lời nhắc") }
            item { OutlinedTextField(message, { message = it.take(300) }, Modifier.fillMaxWidth().testTag("rule_message"),
                placeholder = { Text("Ví dụ: Đã đến lúc nghỉ một chút.") }, minLines = 2, maxLines = 5,
                supportingText = { Text("Để trống để dùng nội dung tự động.") }, shape = RoundedCornerShape(12.dp), colors = fieldColors()) }
            if (inherited != null) item { InfoCard("Thay thế quy tắc nhóm", "${parent?.name.orEmpty()} · ${inherited.contextLabel()}\n$ownerName sẽ dùng quy tắc riêng này.") }
            if (duplicate != null) item { InfoCard("Đã có quy tắc trùng khớp", "Hãy chỉnh sửa quy tắc ${duplicate.contextLabel().lowercase()} hiện có.", warning = true,
                action = "Xem quy tắc", onAction = { onExisting(duplicate.id) }) }
            if (attempted) errors.filter { it.field !in setOf("thresholdMinutes", "repeatEveryMinutes", "repeatMaxCount", "duplicate") }.forEach { error ->
                item { Text(error.message, color = AlertDanger, style = MaterialTheme.typography.bodySmall) }
            }
            item { Surface(color = AlertSurface, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = AlertMuted)
                    Column { Text("TÓM TẮT", style = MaterialTheme.typography.labelSmall, color = AlertMuted); Spacer(Modifier.height(4.dp))
                        Text("$ownerName · ${draft.summary()}${if (draft.repeatEnabled) ". ${draft.repeatPreview()}" else "."}", style = MaterialTheme.typography.bodySmall, color = AlertSecondary) }
                }
            } }
        }
    }
    if (discard) DiscardDialog({ discard = false }, onBack)
}

@Composable
private fun ChoiceCard(selected: Boolean, title: String, subtitle: String, icon: ImageVector, tag: String, onSelect: () -> Unit) {
    Surface(color = if (selected) AlertBlueSoft else AlertSurface, shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(1.dp, AlertBlue) else null,
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onSelect)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(21.dp).padding(top = 2.dp), tint = if (selected) AlertBlue else AlertMuted)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(3.dp)); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AlertSecondary) }
            RadioButton(selected, null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, suffix: String, tag: String, error: String? = null) {
    OutlinedTextField(value, { text -> if (text.length <= 7 && text.all { it.isDigit() }) onChange(text) },
        Modifier.fillMaxWidth().testTag(tag), label = { Text(label) }, suffix = { Text(suffix, color = AlertSecondary) },
        leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(20.dp)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null, shape = RoundedCornerShape(12.dp), colors = fieldColors())
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = AlertBorder, focusedBorderColor = AlertBlue,
    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)

@Composable
private fun DiscardDialog(onDismiss: () -> Unit, onDiscard: () -> Unit) {
    AppAlertDialog(onDismissRequest = onDismiss, title = { Text("Bỏ thay đổi?") }, text = { Text("Các thay đổi chưa lưu sẽ bị bỏ.") },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Bỏ thay đổi", color = AlertDanger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tiếp tục chỉnh sửa") } })
}
