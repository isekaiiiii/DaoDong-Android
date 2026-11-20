package com.ayane.dynamicisland.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToggleActionType {
    TOGGLE,
    TURN_ON,
    TURN_OFF
}

enum class RingerActionType {
    SET_RING,
    SET_VIBRATE,
    TOGGLE
}

enum class MediaActionType {
    PLAY,
    PAUSE,
    TOGGLE_PLAYBACK,
    SKIP
}

sealed class TaskAction {
    data class OpenApp(val packageName: String, val appLabel: String) : TaskAction()
    data class Wifi(val type: ToggleActionType) : TaskAction()
    data class MobileData(val type: ToggleActionType) : TaskAction()
    data class Flashlight(val type: ToggleActionType) : TaskAction()
    data class LowPower(val type: ToggleActionType) : TaskAction()
    data class AirplaneMode(val type: ToggleActionType) : TaskAction()
    data class Bluetooth(val type: ToggleActionType) : TaskAction()
    data class RingerMode(val type: RingerActionType) : TaskAction()
    data class SetBrightness(val level: Int) : TaskAction()
    data class SetVolume(val level: Int) : TaskAction()
    object TakeScreenshot : TaskAction()
    object GoHome : TaskAction()
    data class MediaControls(val type: MediaActionType) : TaskAction()
    data class SendNotification(val title: String, val text: String) : TaskAction()

    fun getDisplayName(): String {
        return when (this) {
            is OpenApp -> "Mở $appLabel"
            is Wifi -> when (type) {
                ToggleActionType.TOGGLE -> "Bật/tắt Wi-Fi"
                ToggleActionType.TURN_ON -> "Bật Wi-Fi"
                ToggleActionType.TURN_OFF -> "Tắt Wi-Fi"
            }
            is MobileData -> when (type) {
                ToggleActionType.TOGGLE -> "Bật/tắt dữ liệu di động"
                ToggleActionType.TURN_ON -> "Bật dữ liệu di động"
                ToggleActionType.TURN_OFF -> "Tắt dữ liệu di động"
            }
            is Flashlight -> when (type) {
                ToggleActionType.TOGGLE -> "Bật/tắt đèn pin"
                ToggleActionType.TURN_ON -> "Bật đèn pin"
                ToggleActionType.TURN_OFF -> "Tắt đèn pin"
            }
            is LowPower -> when (type) {
                ToggleActionType.TOGGLE -> "Bật/tắt tiết kiệm pin"
                ToggleActionType.TURN_ON -> "Bật tiết kiệm pin"
                ToggleActionType.TURN_OFF -> "Tắt tiết kiệm pin"
            }
            is AirplaneMode -> when (type) {
                ToggleActionType.TOGGLE -> "Bật/tắt chế độ máy bay"
                ToggleActionType.TURN_ON -> "Bật chế độ máy bay"
                ToggleActionType.TURN_OFF -> "Tắt chế độ máy bay"
            }
            is Bluetooth -> when (type) {
                ToggleActionType.TOGGLE -> "Bật/tắt Bluetooth"
                ToggleActionType.TURN_ON -> "Bật Bluetooth"
                ToggleActionType.TURN_OFF -> "Tắt Bluetooth"
            }
            is RingerMode -> when (type) {
                RingerActionType.SET_RING -> "Bật chế độ chuông"
                RingerActionType.SET_VIBRATE -> "Bật chế độ rung"
                RingerActionType.TOGGLE -> "Chuyển chuông/rung"
            }
            is SetBrightness -> "Đặt độ sáng ở mức $level%"
            is SetVolume -> "Đặt âm lượng ở mức $level%"
            is TakeScreenshot -> "Chụp ảnh màn hình"
            is GoHome -> "Về màn hình chính"
            is MediaControls -> when (type) {
                MediaActionType.PLAY -> "Phát nội dung"
                MediaActionType.PAUSE -> "Tạm dừng nội dung"
                MediaActionType.TOGGLE_PLAYBACK -> "Phát/tạm dừng nội dung"
                MediaActionType.SKIP -> "Chuyển bài tiếp theo"
            }
            is SendNotification -> "Gửi thông báo"
        }
    }

    fun getIcon(): ImageVector {
        return when (this) {
            is OpenApp -> Icons.Default.Apps
            is Wifi -> Icons.Default.Wifi
            is MobileData -> Icons.Default.SignalCellularAlt
            is Flashlight -> Icons.Default.FlashlightOn
            is LowPower -> Icons.Default.BatteryChargingFull
            is AirplaneMode -> Icons.Default.AirplanemodeActive
            is Bluetooth -> Icons.Default.Bluetooth
            is RingerMode -> when (type) {
                RingerActionType.SET_VIBRATE -> Icons.Default.Vibration
                else -> Icons.Default.NotificationsActive
            }
            is SetBrightness -> Icons.Default.BrightnessMedium
            is SetVolume -> Icons.Default.VolumeUp
            is TakeScreenshot -> Icons.Default.Screenshot
            is GoHome -> Icons.Default.Home
            is MediaControls -> when (type) {
                MediaActionType.PLAY -> Icons.Default.PlayArrow
                MediaActionType.PAUSE -> Icons.Default.Pause
                MediaActionType.TOGGLE_PLAYBACK -> Icons.Default.PlayCircleOutline
                MediaActionType.SKIP -> Icons.Default.SkipNext
            }
            is SendNotification -> Icons.Default.NotificationAdd
        }
    }
}

data class TaskShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val iconName: String = "Bolt",
    val showInBubble: Boolean = true,
    val actions: List<TaskAction> = emptyList()
)
