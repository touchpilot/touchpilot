package dev.touchpilot.app.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat


data class CompatibilityCheck(
    val title: String,
    val status: String,
    val details: String,
    val required: Boolean,
    val passed: Boolean,
)

data class DeviceCompatibilitySummary(
    val deviceLabel: String,
    val checks: List<CompatibilityCheck>,
) {
    val isReadyForRun: Boolean
        get() = checks.filter { it.required }.all { it.passed }

    val hasBlockingIssues: Boolean
        get() = checks.any { it.required && !it.passed }
}

object CompatibilityOnboarding {
    private const val MIN_SDK = 26

    fun buildSummary(context: Context, accessibilityConnected: Boolean): DeviceCompatibilitySummary {
        val checks = mutableListOf<CompatibilityCheck>()
        val api = Build.VERSION.SDK_INT

        checks += CompatibilityCheck(
            title = "Android version",
            status = if (api >= MIN_SDK) "PASS" else "FAIL",
            details = "Detected Android ${Build.VERSION.RELEASE} (API $api). Minimum supported: $MIN_SDK.",
            required = true,
            passed = api >= MIN_SDK,
        )

        val manufacturer = Build.MANUFACTURER.ifBlank { "unknown" }
        val brand = Build.BRAND.ifBlank { "unknown" }
        val model = Build.MODEL.ifBlank { "unknown" }
        checks += CompatibilityCheck(
            title = "OEM / skin",
            status = "INFO",
            details = "OEM profile: $manufacturer · brand=$brand · model=$model.",
            required = false,
            passed = true,
        )

        checks += CompatibilityCheck(
            title = "Accessibility service",
            status = if (accessibilityConnected) "PASS" else "FAIL",
            details = if (accessibilityConnected) {
                "TouchPilot accessibility service is connected."
            } else {
                "Enable Accessibility -> TouchPilot Control before first run."
            },
            required = true,
            passed = accessibilityConnected,
        )

        checks += permissionCheck(context)

        checks += CompatibilityCheck(
            title = "Battery optimization",
            status = if (isBatteryOptimizationIgnored(context)) "PASS" else "WARN",
            details = if (isBatteryOptimizationIgnored(context)) {
                "Battery optimization is currently ignored for TouchPilot."
            } else {
                "Consider excluding TouchPilot from battery optimization for stable background behavior."
            },
            required = false,
            passed = true,
        )

        val deviceLabel = "$manufacturer · Android ${Build.VERSION.RELEASE} (API $api)"
        return DeviceCompatibilitySummary(deviceLabel = deviceLabel, checks = checks)
    }

    private fun permissionCheck(context: Context): CompatibilityCheck {
        val requestedPermissions = runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .filter { it.isNotBlank() }
        }.getOrElse { emptyList() }

        val runtimePermissions = requestedPermissions.filter { permission ->
            runCatching {
                val info = context.packageManager.getPermissionInfo(permission, 0)
                (info.protectionLevel and PermissionInfo.PROTECTION_DANGEROUS) != 0
            }.getOrElse { false }
        }

        val denied = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        val details = when {
            runtimePermissions.isEmpty() -> {
                "No runtime dangerous permissions are required by the manifest."
            }
            denied.isEmpty() -> {
                "Required runtime permissions granted: ${runtimePermissions.joinToString(", ")}"
            }
            else -> {
                "Missing required runtime permissions: ${denied.joinToString(", ")}"
            }
        }

        return CompatibilityCheck(
            title = "Required permissions",
            status = if (denied.isEmpty()) "PASS" else "FAIL",
            details = details,
            required = true,
            passed = denied.isEmpty(),
        )
    }

    private fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
