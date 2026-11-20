package com.ayane.dynamicisland.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object TaskIcons {
    val icons: List<Pair<String, ImageVector>> = listOf(
        "Bolt" to Icons.Default.Bolt,
        "Flashlight" to Icons.Default.FlashlightOn,
        "Wifi" to Icons.Default.Wifi,
        "Cellular" to Icons.Default.SignalCellularAlt,
        "Bluetooth" to Icons.Default.Bluetooth,
        "Battery" to Icons.Default.BatteryChargingFull,
        "Airplane" to Icons.Default.AirplanemodeActive,
        "Ring" to Icons.Default.NotificationsActive,
        "Vibrate" to Icons.Default.Vibration,
        "Volume" to Icons.Default.VolumeUp,
        "Apps" to Icons.Default.Apps,
        "Star" to Icons.Default.Star,
        "Favorite" to Icons.Default.Favorite,
        "Rocket" to Icons.Default.RocketLaunch,
        "Home" to Icons.Default.Home,
        "Work" to Icons.Default.Work,
        "School" to Icons.Default.School,
        "Fitness" to Icons.Default.FitnessCenter,
        "Car" to Icons.Default.DirectionsCar,
        "Walk" to Icons.Default.DirectionsRun,
        "Music" to Icons.Default.MusicNote,
        "Headphones" to Icons.Default.Headphones,
        "Camera" to Icons.Default.CameraAlt,
        "Video" to Icons.Default.Videocam,
        "Call" to Icons.Default.Call,
        "Chat" to Icons.Default.Chat,
        "Mail" to Icons.Default.Email,
        "Alarm" to Icons.Default.Alarm,
        "Timer" to Icons.Default.Timer,
        "Lightbulb" to Icons.Default.Lightbulb,
        "Sun" to Icons.Default.WbSunny,
        "Moon" to Icons.Default.DarkMode,
        "Cloud" to Icons.Default.Cloud,
        "Shield" to Icons.Default.Shield,
        "Lock" to Icons.Default.Lock,
        "Key" to Icons.Default.Key,
        "Settings" to Icons.Default.Settings,
        "Build" to Icons.Default.Build,
        "Palette" to Icons.Default.Palette,
        "Coffee" to Icons.Default.LocalCafe,
        "Restaurant" to Icons.Default.Restaurant,
        "Store" to Icons.Default.ShoppingBag,
        "Cart" to Icons.Default.ShoppingCart,
        "Navigation" to Icons.Default.Navigation,
        "Explore" to Icons.Default.Explore,
        "Map" to Icons.Default.Map,
        "Download" to Icons.Default.Download,
        "Upload" to Icons.Default.Upload,
        "Refresh" to Icons.Default.Refresh,
        "Code" to Icons.Default.Code,
        "Terminal" to Icons.Default.Terminal,
        "Check" to Icons.Default.CheckCircle,
        "Done" to Icons.Default.DoneAll
    )

    private val map: Map<String, ImageVector> = icons.toMap()

    fun getIcon(name: String): ImageVector {
        return map[name] ?: Icons.Default.Bolt
    }

    fun getVietnameseLabel(name: String): String = when (name) {
        "Bolt" -> "Tia sét"
        "Flashlight" -> "Đèn pin"
        "Wifi" -> "Wi-Fi"
        "Cellular" -> "Mạng di động"
        "Bluetooth" -> "Bluetooth"
        "Battery" -> "Pin"
        "Airplane" -> "Máy bay"
        "Ring" -> "Chuông"
        "Vibrate" -> "Rung"
        "Volume" -> "Âm lượng"
        "Apps" -> "Ứng dụng"
        "Star" -> "Ngôi sao"
        "Favorite" -> "Yêu thích"
        "Rocket" -> "Tên lửa"
        "Home" -> "Trang chủ"
        "Work" -> "Công việc"
        "School" -> "Trường học"
        "Fitness" -> "Thể thao"
        "Car" -> "Ô tô"
        "Walk" -> "Đi bộ"
        "Music" -> "Âm nhạc"
        "Headphones" -> "Tai nghe"
        "Camera" -> "Máy ảnh"
        "Video" -> "Video"
        "Call" -> "Cuộc gọi"
        "Chat" -> "Trò chuyện"
        "Mail" -> "Thư"
        "Alarm" -> "Báo thức"
        "Timer" -> "Hẹn giờ"
        "Lightbulb" -> "Bóng đèn"
        "Sun" -> "Mặt trời"
        "Moon" -> "Mặt trăng"
        "Cloud" -> "Đám mây"
        "Shield" -> "Bảo vệ"
        "Lock" -> "Khóa"
        "Key" -> "Chìa khóa"
        "Settings" -> "Cài đặt"
        "Build" -> "Công cụ"
        "Palette" -> "Bảng màu"
        "Coffee" -> "Cà phê"
        "Restaurant" -> "Nhà hàng"
        "Store" -> "Cửa hàng"
        "Cart" -> "Giỏ hàng"
        "Navigation" -> "Điều hướng"
        "Explore" -> "Khám phá"
        "Map" -> "Bản đồ"
        "Download" -> "Tải xuống"
        "Upload" -> "Tải lên"
        "Refresh" -> "Làm mới"
        "Code" -> "Mã nguồn"
        "Terminal" -> "Dòng lệnh"
        "Check" -> "Xác nhận"
        "Done" -> "Hoàn tất"
        else -> name
    }
}

