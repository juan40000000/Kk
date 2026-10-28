package com.experimento.wifiradar

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/** Una red WiFi vista en un escaneo. */
data class Net(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val freq: Int,
    val caps: String,
    val standard: String = "",
    val widthMhz: Int = 20,
) {
    val channel: Int get() = WifiUtil.channel(freq)
    val band: String get() = WifiUtil.band(freq)
    val security: String get() = WifiUtil.security(caps)
    val distance: Double get() = WifiUtil.distanceMeters(rssi, freq)
    val vendor: String get() = WifiUtil.vendor(bssid)
    val randomMac: Boolean get() = WifiUtil.isLocallyAdministered(bssid)
    val displaySsid: String get() = ssid.ifEmpty { "(red oculta)" }
    val hint: String? get() = WifiUtil.hint(this)
}

object WifiUtil {

    fun channel(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2407) / 5
        freq in 5160..5885 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }

    fun band(freq: Int): String = when {
        freq < 3000 -> "2.4 GHz"
        freq < 5925 -> "5 GHz"
        else -> "6 GHz"
    }

    fun security(caps: String): String = when {
        caps.contains("OWE") -> "OWE"
        caps.contains("SAE") && caps.contains("PSK") -> "WPA2/WPA3"
        caps.contains("SAE") -> "WPA3"
        caps.contains("EAP") -> "Enterprise"
        caps.contains("WPA2") || caps.contains("RSN") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        else -> "Abierta"
    }

    /** Hay WPS anunciado (históricamente vulnerable a ataques de PIN). */
    fun hasWps(caps: String) = caps.contains("WPS")

    /**
     * Distancia estimada con el modelo de pérdida en espacio libre (FSPL).
     * En interiores con paredes el valor real suele ser bastante menor.
     */
    fun distanceMeters(rssi: Int, freq: Int): Double {
        val exp = (27.55 - 20 * log10(freq.toDouble()) + abs(rssi)) / 20.0
        return 10.0.pow(exp)
    }

    /** Barras de señal tipo teléfono. */
    fun bars(rssi: Int): String = when {
        rssi >= -55 -> "▂▄▆█"
        rssi >= -67 -> "▂▄▆_"
        rssi >= -78 -> "▂▄__"
        rssi >= -88 -> "▂___"
        else -> "____"
    }

    /** Bit "localmente administrada": MAC aleatoria/virtual (hotspots de móviles, repetidores...). */
    fun isLocallyAdministered(bssid: String): Boolean {
        val first = bssid.take(2).toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }

    fun vendor(bssid: String): String {
        if (isLocallyAdministered(bssid)) return "MAC aleatoria"
        return OUI[bssid.take(8).uppercase()] ?: "Desconocido"
    }

    fun hint(n: Net): String? {
        val s = n.ssid.lowercase()
        return when {
            s.startsWith("direct-") -> "Wi-Fi Direct (impresora / TV / proyector)"
            listOf("iphone", "galaxy", "redmi", "xiaomi", "moto ", "pixel", "huawei", "poco", "oneplus")
                .any { s.contains(it) } -> "Probable hotspot de un móvil"
            s.contains("chromecast") || s.contains("roku") || s.contains("firetv") -> "Dispositivo de streaming"
            s.startsWith("esp") || n.vendor.startsWith("Espressif") -> "Dispositivo IoT (ESP8266/ESP32)"
            s.contains("hp-print") || s.contains("epson") || s.contains("canon") || s.contains("brother") -> "Impresora"
            s.contains("tesla") || s.contains("car") && s.contains("play") -> "Vehículo"
            s.contains("cam") || s.contains("ipc") || s.contains("tapo") -> "Posible cámara IP"
            n.randomMac && n.ssid.isEmpty() -> "Red oculta con MAC virtual"
            n.randomMac -> "MAC virtual: hotspot móvil o red secundaria de un router"
            n.vendor.startsWith("Raspberry") -> "Raspberry Pi"
            else -> null
        }
    }

    /** Pequeña tabla de OUIs (primeros 3 bytes de la MAC) de fabricantes comunes. */
    private val OUI: Map<String, String> = buildMap {
        fun add(name: String, vararg prefixes: String) = prefixes.forEach { put(it, name) }
        add("TP-Link", "50:C7:BF", "14:CC:20", "F4:F2:6D", "C0:4A:00", "EC:08:6B", "60:E3:27", "A0:F3:C1")
        add("Apple", "00:03:93", "00:1C:B3", "F0:18:98", "AC:BC:32")
        add("Ubiquiti", "24:A4:3C", "04:18:D6", "80:2A:A8", "F0:9F:C2", "78:8A:20", "FC:EC:DA")
        add("Cisco", "00:40:96")
        add("Netgear", "A0:40:A0", "20:4E:7F", "C4:04:15")
        add("Huawei", "00:E0:FC")
        add("Xiaomi", "64:09:80", "28:6C:07", "34:CE:00")
        add(
            "Espressif (IoT)", "24:0A:C4", "30:AE:A4", "A4:CF:12", "24:6F:28", "84:F3:EB",
            "5C:CF:7F", "18:FE:34", "60:01:94", "BC:DD:C2", "CC:50:E3",
        )
        add("Raspberry Pi", "B8:27:EB", "DC:A6:32", "E4:5F:01")
        add("Google", "F4:F5:D8")
        add("MikroTik", "4C:5E:0C", "D4:CA:6D", "6C:3B:6B", "E4:8D:8C")
        add("D-Link", "00:05:5D", "1C:7E:E5")
        add("Linksys", "00:14:BF", "C0:56:27")
    }
}
