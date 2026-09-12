package dev.lordierclaw.lunaappalert.core

fun TriggerType.label(): String = when (this) {
    TriggerType.ON_LAUNCH -> "Khi mở ứng dụng"
    TriggerType.CONTINUOUS_USE -> "Sử dụng liên tục"
    TriggerType.DAILY_TOTAL -> "Tổng thời gian hôm nay"
}

fun AlertType.label(): String = when (this) {
    AlertType.NOTIFICATION -> "Thông báo"
    AlertType.OVERLAY -> "Cảnh báo trên màn hình"
}

fun RuleStatus.label(): String = when (this) {
    RuleStatus.ACTIVE -> "Đang hoạt động"
    RuleStatus.OFF -> "Đã tắt"
    RuleStatus.PAUSED_BY_GROUP -> "Tạm dừng bởi nhóm"
    RuleStatus.PAUSED_BY_APP -> "Ứng dụng đã tạm dừng"
    RuleStatus.NEEDS_PERMISSION -> "Cần cấp quyền"
    RuleStatus.OVERRIDDEN -> "Được thay thế bởi quy tắc ứng dụng"
    RuleStatus.NOT_INSTALLED -> "Chưa cài đặt"
    RuleStatus.MONITORING_PAUSED -> "Đã tạm dừng theo dõi"
}

fun Rule.contextLabel(): String = when (triggerType) {
    TriggerType.ON_LAUNCH -> "Khi mở ứng dụng"
    TriggerType.CONTINUOUS_USE -> "Sử dụng liên tục · ${thresholdMinutes ?: 0} phút"
    TriggerType.DAILY_TOTAL -> "Hôm nay · ${thresholdMinutes ?: 0} phút"
}

fun Rule.summary(): String = when (triggerType) {
    TriggerType.ON_LAUNCH -> "Khi mở ứng dụng · ${alertType.label()}"
    TriggerType.CONTINUOUS_USE -> "Sau ${thresholdMinutes ?: 0} phút liên tục · ${alertType.label()}"
    TriggerType.DAILY_TOTAL -> "Sau ${thresholdMinutes ?: 0} phút hôm nay · ${alertType.label()}"
}

fun Rule.displayMessage(appName: String): String = customMessage.takeIf { it.isNotBlank() }
    ?: when (triggerType) {
        TriggerType.ON_LAUNCH -> "Bạn vừa mở $appName."
        TriggerType.CONTINUOUS_USE -> "Bạn đã sử dụng $appName liên tục ${thresholdMinutes ?: 0} phút."
        TriggerType.DAILY_TOTAL -> "Bạn đã sử dụng $appName ${thresholdMinutes ?: 0} phút hôm nay."
    }

fun Rule.repeatPreview(): String {
    if (triggerType != TriggerType.CONTINUOUS_USE || !repeatEnabled) return ""
    val threshold = thresholdMinutes?.takeIf { it > 0 } ?: return ""
    val every = repeatEveryMinutes?.takeIf { it > 0 } ?: return ""
    val count = repeatMaxCount?.takeIf { it > 0 } ?: return ""
    val first = (0..minOf(count, 3)).joinToString(", ") { (threshold.toLong() + every.toLong() * it).toString() }
    return if (count > 3) "Cảnh báo ở phút $first…; lặp thêm $count lần."
        else "Cảnh báo ở phút $first."
}

fun List<ResolvedRule>.effectiveSummary(): String {
    val active = filter { it.status == RuleStatus.ACTIVE }
    if (active.isEmpty()) {
        return firstOrNull { it.status != RuleStatus.OFF }?.status?.label()
            ?: if (isEmpty()) "Chưa có quy tắc" else "Quy tắc đã tắt"
    }
    return active.sortedWith(compareBy({ it.rule.triggerType.ordinal }, { it.rule.thresholdMinutes ?: 0 }))
        .joinToString(" + ") {
            when (it.rule.triggerType) {
                TriggerType.ON_LAUNCH -> "Khi mở"
                TriggerType.CONTINUOUS_USE -> "${it.rule.thresholdMinutes} phút"
                TriggerType.DAILY_TOTAL -> "${it.rule.thresholdMinutes} phút hôm nay"
            }
        }
}
