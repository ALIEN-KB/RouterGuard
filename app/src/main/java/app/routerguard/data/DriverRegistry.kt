package app.routerguard.data

import android.content.Context
import java.io.File

/**
 * Finds drivers in three places, in this order:
 *  1. the phone's driver folder (filesDir/drivers), where installed or downloaded drivers live
 *  2. drivers shipped inside the app (assets/drivers), copied to the folder on first use
 *  3. nothing: the app tells the user and offers guided setup
 */
class DriverRegistry(private val context: Context) {
    private val dir = File(context.filesDir, "drivers").apply { mkdirs() }

    fun installedIds(): List<String> =
        dir.listFiles { f -> f.extension == "json" }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

    /** Cheap yes/no for the router list: is a driver built in, installed, or shipped with the app? */
    fun available(driverId: String?): Boolean {
        if (driverId == null) return false
        if (driverId == Catalog.HUAWEI || File(dir, "$driverId.json").exists()) return true
        return runCatching { context.assets.open("drivers/$driverId.json").close(); true }.getOrDefault(false)
    }

    fun check(driverId: String?): DriverStatus {
        if (driverId == null) return DriverStatus.Missing
        if (driverId == Catalog.HUAWEI) return DriverStatus.BuiltIn
        val file = File(dir, "$driverId.json")
        if (file.exists()) {
            return runCatching { DriverStatus.Ready(Recipe.parse(file.readText())) }
                .getOrElse { DriverStatus.Broken(it.message ?: "Unreadable driver file") }
        }
        val bundled = runCatching { context.assets.open("drivers/$driverId.json").bufferedReader().readText() }.getOrNull()
            ?: return DriverStatus.Missing
        return runCatching {
            val r = Recipe.parse(bundled)
            file.writeText(bundled)
            DriverStatus.JustInstalled(r)
        }.getOrElse { DriverStatus.Broken(it.message ?: "Bad bundled driver") }
    }

    /** Installs a driver the user was given (a .json file). Returns the recipe id. */
    fun install(text: String): Recipe {
        val r = Recipe.parse(text)
        File(dir, "${r.id}.json").writeText(text)
        return r
    }

    fun remove(driverId: String) { File(dir, "$driverId.json").delete() }

    /** Builds a ready-to-use driver for a stored status, or null when this router has none. */
    fun create(status: DriverStatus, host: String, user: String, password: String, network: android.net.Network?): RouterDriver? {
        val recipe = when (status) {
            is DriverStatus.Ready -> status.recipe
            is DriverStatus.JustInstalled -> status.recipe
            else -> return null
        }
        return RecipeDriver(recipe, host, user, password) { url ->
            (network?.openConnection(url) ?: url.openConnection())
        }
    }
}
