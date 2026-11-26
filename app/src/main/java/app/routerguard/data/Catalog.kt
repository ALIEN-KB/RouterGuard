package app.routerguard.data

/** One router model people commonly have in Pakistan. */
data class RouterModel(
    val id: String,
    val brand: String,
    val model: String,
    val seenOn: String,
    /** The driver this model uses, or null when no driver exists yet. */
    val driverId: String?,
    val defaultHost: String,
    val defaultUser: String,
)

object Catalog {
    const val HUAWEI = "huawei-hg8xxx"

    val models: List<RouterModel> = listOf(
        RouterModel("hg8546m", "Huawei", "HG8546M / HG8145V5 / HG8245 (GPON ONT)", "PTCL, Nayatel, StormFiber, Transworld", HUAWEI, "192.168.100.1", "root"),
        RouterModel("zte-f670l", "ZTE", "ZXHN F670L / F660 / F609 (GPON ONT)", "PTCL, StormFiber, Nayatel", "zte-zxhn", "192.168.1.1", "admin"),
        RouterModel("fiberhome", "FiberHome", "HG6245D / AN5506 (GPON ONT)", "PTCL, Nayatel, Cybernet", "fiberhome-an55", "192.168.1.1", "admin"),
        RouterModel("tplink", "TP-Link", "Archer C6 / TL-WR841N / Deco", "Home and office routers", "tplink-web", "192.168.0.1", "admin"),
        RouterModel("tenda", "Tenda", "AC10 / F3 / N301", "Home routers", "tenda-web", "192.168.0.1", "admin"),
        RouterModel("dlink", "D-Link", "DIR-615 / DIR-825", "Home routers", "dlink-web", "192.168.0.1", "admin"),
        RouterModel("huawei-lte", "Huawei", "B311 / B315 / B618 (4G/LTE)", "Jazz, Zong, Ufone, Telenor 4G", "huawei-lte", "192.168.8.1", "admin"),
    )

    fun byDriver(driverId: String): RouterModel? = models.firstOrNull { it.driverId == driverId }
}

/** What the app knows about the driver for the chosen router. */
sealed class DriverStatus {
    /** Built into the app and verified against a real router. */
    object BuiltIn : DriverStatus()
    /** Found and loaded from the phone's driver folder. */
    data class Ready(val recipe: Recipe) : DriverStatus()
    /** Shipped inside the app and just copied to the driver folder. */
    data class JustInstalled(val recipe: Recipe) : DriverStatus()
    /** No driver exists yet for this router: the guided setup can create one. */
    object Missing : DriverStatus()
    data class Broken(val reason: String) : DriverStatus()
}
