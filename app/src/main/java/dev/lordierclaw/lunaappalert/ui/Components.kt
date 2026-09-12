package dev.lordierclaw.lunaappalert.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.lordierclaw.lunaappalert.R
import dev.lordierclaw.lunaappalert.core.*
import dev.lordierclaw.lunaappalert.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppScreen(title: String, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, bottom: @Composable () -> Unit = {},
    floatingAction: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    // With a large software keyboard in a short landscape window, a top bar
    // leaves too little height to read the focused input. Put the 48dp actions
    // beside the form so its remaining height is available for editing.
    val keyboardRow = WindowInsets.isImeVisible && maxHeight < 200.dp && onBack != null
    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.White,
        topBar = {
            if (!keyboardRow) {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title == "App Alert" && onBack == null) {
                        Icon(Icons.Outlined.Timer, null, Modifier.size(24.dp), tint = AlertBlue)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("navigate_back")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại")
                } }, actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = AlertInk))
            }
        }, bottomBar = { if (!keyboardRow) bottom() }, floatingActionButton = floatingAction) { padding ->
        Row(Modifier.fillMaxSize().padding(padding), verticalAlignment = Alignment.CenterVertically) {
            if (keyboardRow) IconButton(onClick = { onBack?.invoke() }, modifier = Modifier.testTag("navigate_back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại")
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    content(PaddingValues(horizontal = if (keyboardRow) 8.dp else 20.dp, vertical = if (keyboardRow) 0.dp else 12.dp))
                }
            }
            if (keyboardRow) Row(content = actions)
        }
    }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = Icons.Outlined.Add) {
    FilledTonalButton(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = AlertBlueSoft, contentColor = AlertBlue)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun Footer(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, tonalElevation = 0.dp) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/** Keep a 48dp toolbar save action when a short window or keyboard leaves little room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun compactEditor(): Boolean {
    val height = LocalConfiguration.current.screenHeightDp
    return height < 480 || (WindowInsets.isImeVisible && height < 600)
}

@Composable
fun EditorSaveAction(enabled: Boolean, tag: String, onSave: () -> Unit) {
    IconButton(onClick = onSave, enabled = enabled, modifier = Modifier.testTag(tag)) {
        Icon(Icons.Outlined.Check, "Lưu thay đổi", tint = if (enabled) AlertBlue else AlertMuted)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null, tag: String = "") {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (action != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.testTag(tag)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(action, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (subtitle != null) Text(subtitle, color = AlertSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, background: Color = Color.White, tint: Color = AlertBlue) {
    Box(modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
fun AppLogo(size: Dp = 64.dp) { Image(painterResource(R.drawable.ic_app_alert), "Logo App Alert", Modifier.size(size)) }

@Composable
fun AppIcon(packageName: String, vm: AppViewModel, size: Dp = 42.dp) {
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { runCatching { vm.icon(packageName)?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) Image(bitmap!!, null, Modifier.size(size).clip(RoundedCornerShape(11.dp)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(AlertBlueSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Apps, null, Modifier.size(size * .55f), tint = AlertBlue)
    }
}

@Composable
fun AppToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String, tag: String) {
    Switch(checked, onCheckedChange, modifier = Modifier.testTag(tag).semantics { contentDescription = description },
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AlertBlue,
            checkedBorderColor = AlertBlue, uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFD0D5DD), uncheckedBorderColor = Color(0xFFD0D5DD)))
}

@Composable
fun StatusPill(text: String, color: Color = AlertSecondary, background: Color = AlertSurface) {
    Surface(color = background, shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun InfoCard(title: String, body: String, icon: ImageVector = Icons.Outlined.Info, warning: Boolean = false,
    action: String? = null, onAction: (() -> Unit)? = null) {
    val tint = if (warning) AlertWarning else AlertBlue
    Surface(color = if (warning) Color(0xFFFFF8EB) else AlertBlueSoft, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(21.dp), tint = tint)
            Column(Modifier.weight(1f)) {
                Text(title, color = if (warning) AlertWarning else AlertInk, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(body, color = AlertSecondary, style = MaterialTheme.typography.bodySmall)
                if (action != null && onAction != null) TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(action) }
            }
        }
    }
}

@Composable
fun AppRow(app: TrackedApp, vm: AppViewModel, summary: String, onClick: () -> Unit, background: Color = AlertSurface) {
    Surface(color = background, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onClick).testTag("app_${app.id}").padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app.packageName, vm)
            Column(Modifier.weight(1f)) {
                Text(app.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, color = AlertSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AppToggle(app.enabled, { vm.toggleApp(app, it) }, "Theo dõi ${app.displayName}", "app_toggle_${app.id}")
        }
    }
}

fun TriggerType.icon(): ImageVector = when (this) {
    TriggerType.ON_LAUNCH -> Icons.Outlined.OpenInNew
    TriggerType.CONTINUOUS_USE -> Icons.Outlined.Timer
    TriggerType.DAILY_TOTAL -> Icons.Outlined.Today
}

@Composable
fun RuleCard(rule: Rule, status: RuleStatus? = null, supporting: String? = null, editable: Boolean = true,
    onEdit: () -> Unit, onToggle: ((Boolean) -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = AlertSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onEdit).testTag("rule_${rule.id}").padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(rule.triggerType.icon(), Modifier.padding(top = 3.dp).size(34.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(rule.triggerType.label(), style = MaterialTheme.typography.titleSmall)
                    if (rule.triggerType != TriggerType.ON_LAUNCH) Text("Sau ${rule.thresholdMinutes} phút${if (rule.triggerType == TriggerType.DAILY_TOTAL) " hôm nay" else ""}",
                        style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
                    if (rule.repeatEnabled) Text("Lặp mỗi ${rule.repeatEveryMinutes} phút · ${rule.repeatMaxCount} lần", style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
                    Spacer(Modifier.height(5.dp))
                    StatusPill(if (rule.alertType == AlertType.OVERLAY) "Trên màn hình" else "Thông báo", background = Color.White)
                }
                if (editable && onToggle != null) AppToggle(rule.enabled, onToggle, "Bật quy tắc ${rule.triggerType.label()}", "rule_toggle_${rule.id}")
                if (editable) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("rule_menu_${rule.id}")) { Icon(Icons.Outlined.MoreVert, "Tùy chọn quy tắc", Modifier.size(20.dp)) }
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                        DropdownMenuItem(text = { Text("Chỉnh sửa") }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                        if (onDelete != null) DropdownMenuItem(text = { Text("Xóa quy tắc", color = AlertDanger) }, modifier = Modifier.testTag("rule_delete_${rule.id}"), onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = AlertDanger) })
                    }
                }
            }
            if (supporting != null) { Spacer(Modifier.height(8.dp)); Text(supporting, style = MaterialTheme.typography.bodySmall, color = AlertSecondary) }
            if (status != null && status != RuleStatus.ACTIVE) {
                Spacer(Modifier.height(8.dp)); StatusPill(status.label(),
                    color = if (status == RuleStatus.NEEDS_PERMISSION) AlertWarning else if (status == RuleStatus.OVERRIDDEN) AlertBlue else AlertSecondary,
                    background = if (status == RuleStatus.OVERRIDDEN) AlertBlueSoft else Color.White)
            }
            if (rule.customMessage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp)) {
                    Text("“${rule.customMessage}”", Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic, color = AlertSecondary)
                }
            }
        }
    }
}

@Composable
fun EmptyRules(onAdd: () -> Unit) {
    Surface(color = AlertSurface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.NotificationsNone, null, tint = AlertMuted, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp)); Text("Chưa có quy tắc", style = MaterialTheme.typography.titleSmall)
            Text("Chọn thời điểm bạn muốn được nhắc.", style = MaterialTheme.typography.bodySmall, color = AlertSecondary)
            TextButton(onClick = onAdd) { Text("Thêm quy tắc") }
        }
    }
}

/** Dialogs create a separate Compose semantics root from the navigation host. */
@Composable
fun AppAlertDialog(onDismissRequest: () -> Unit, title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null, confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismissRequest,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = title, text = text, confirmButton = confirmButton, dismissButton = dismissButton)
}
