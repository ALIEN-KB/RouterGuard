package app.routerguard.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.routerguard.MainActivity
import app.routerguard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Notifier {
    private const val CHANNEL = "new_devices"

    fun newDevices(context: Context, devices: List<LiveDevice>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "New devices", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tells you when an unknown device joins your Wi-Fi" }
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val names = devices.joinToString { it.name.ifBlank { it.mac } }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setContentTitle(if (devices.size == 1) "New device on your Wi-Fi" else "${devices.size} new devices on your Wi-Fi")
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }
}

/** Periodic background check: applies block schedules and looks for new devices. */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val settings = AppSettings(ctx)
        val credStore = CredentialStore(ctx)
        if (credStore.mode() == "browser") return@withContext Result.success()
        val creds = credStore.load() ?: return@withContext Result.success()
        val network = RouterClient.wifiNetwork(ctx) ?: return@withContext Result.success()
        val store = DeviceStore(ctx)
        val known = store.load()
        val schedules = settings.schedules.filter { it.enabled }
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val owned = settings.scheduleOwned.toMutableSet()

        try {
            val snap = RouterClient.session(app.routerguard.buildDriver(app.routerguard.data.DriverRegistry(ctx), app.routerguard.driverIdOf(credStore.mode()), creds, network)) {
                var current = snapshot()
                var changed = false
                for (s in schedules) {
                    val blocked = current.filter.any { it.mac == s.mac }
                    val shouldBlock = s.isActive(nowMin)
                    if (shouldBlock && !blocked) {
                        block(s.mac, current.live.firstOrNull { it.mac == s.mac }?.ssid ?: "SSID-1")
                        owned.add(s.mac)
                        changed = true
                    } else if (!shouldBlock && blocked && s.mac in owned) {
                        unblock(s.mac)
                        owned.remove(s.mac)
                        changed = true
                    } else if (!shouldBlock && !blocked) {
                        owned.remove(s.mac)
                    }
                }
                if (changed) current = snapshot()
                current
            }
            settings.scheduleOwned = owned

            val knownMacs = known.map { it.mac }.toSet()
            // The very first run only learns the existing devices, so it never spams alerts.
            val fresh = if (known.isEmpty()) emptyList() else snap.live.filter { it.online && it.mac !in knownMacs }
            store.save(Merge.merge(known, snap, System.currentTimeMillis()))
            if (settings.alerts && fresh.isNotEmpty()) Notifier.newDevices(ctx, fresh)
        } catch (_: Exception) {
            // Router busy, unreachable or password changed: just try again next time.
        }
        Result.success()
    }
}

object Scheduler {
    private const val NAME = "router_check"

    fun sync(context: Context) {
        val s = AppSettings(context)
        val wm = WorkManager.getInstance(context)
        val hasSchedules = s.schedules.any { it.enabled }
        if (!s.alerts && !hasSchedules) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val minutes = if (hasSchedules) 15L else s.intervalMin.toLong().coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<CheckWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
