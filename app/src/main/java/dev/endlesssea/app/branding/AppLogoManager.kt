package dev.endlesssea.app.branding

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.app.di.AppPrefs
import javax.inject.Inject
import javax.inject.Singleton

/** Only launcher aliases change; MainActivity remains enabled for navigation and video intents. */
@Singleton
class AppLogoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPrefs,
) {
    @Synchronized
    fun select(id: String): Result<Unit> {
        val logo = AppLogos.resolve(id)
        val pm = context.packageManager
        val components = AppLogos.all.associateWith { ComponentName(context.packageName, it.aliasClass) }
        val before = components.values.associateWith(pm::getComponentEnabledSetting)
        val desired = components.map { (choice, component) ->
            component to if (choice.id == logo.id) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        fun effective(component: ComponentName): Int {
            val value = before.getValue(component)
            return if (value != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) value
                else if (component.className == AppLogos.resolve(null).aliasClass) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        return runCatching {
            val changes = desired.filter { (component, state) -> effective(component) != state }
            if (Build.VERSION.SDK_INT >= 33) {
                if (changes.isNotEmpty()) pm.setComponentEnabledSettings(changes.map { (component, state) ->
                    PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                })
            } else {
                // Enable first: never leave an installed app without a launcher entry.
                changes.sortedBy { (_, state) -> if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) 0 else 1 }
                    .forEach { (component, state) -> pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
            }
            prefs.setAppLogo(logo.id)
        }.onFailure {
            // Keep preferences and components consistent if a vendor launcher rejects an update.
            before.entries.sortedBy { (component, _) -> if (effective(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) 0 else 1 }
                .forEach { (component, state) -> runCatching { pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) } }
        }
    }
}
