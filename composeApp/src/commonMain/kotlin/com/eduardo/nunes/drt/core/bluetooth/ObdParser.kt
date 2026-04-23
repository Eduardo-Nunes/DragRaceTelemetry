package com.eduardo.nunes.drt.core.bluetooth

class ObdParser(
    private val listener: ObdDataListener,
    private val log: (String) -> Unit
) {
    companion object {
        private const val PREFIX_SPEED = "410D"
        private const val PREFIX_RPM = "410C"
        private const val MAX_VALID_SPEED = 254
        private const val IGNORED_SPEED_BUFFER = 255
    }

    fun processResponse(response: String) {
        // Limpeza idiomática e mais performática do que múltiplos 'replace'
        val data = response.filterNot { it.isWhitespace() || it == '>' }.uppercase()
        try {
            when {
                data.contains(PREFIX_SPEED) -> parseSpeed(data)
                data.contains(PREFIX_RPM) -> parseRpm(data)
            }
        } catch (e: NumberFormatException) {
            log("Parser Error: Invalid Hex in '$data', cause: ${e.message}")
        } catch (e: Exception) {
            log("Parser Error: ${e.message} in '$data'")
        }
    }

    private fun parseSpeed(data: String) {
        // substringAfter já pega tudo que vem depois do prefixo, evitando indexOf
        val payload = data.substringAfter(PREFIX_SPEED)
        // Guard Clause (Early Return) para evitar aninhamento de IFs
        if (payload.length < 2) return
        // Pega apenas os 2 primeiros caracteres
        val speed = payload.take(2).toInt(16)
        when {
            speed <= MAX_VALID_SPEED -> {
                listener.onDataParsed(ObdPid.SPEED, speed)
                log("Parsed Speed: $speed km/h")
            }
            speed == IGNORED_SPEED_BUFFER -> {
                log("Speed Warning: 255 received (Ignored)")
            }
        }
    }

    private fun parseRpm(data: String) {
        val payload = data.substringAfter(PREFIX_RPM)
        // Guard Clause
        if (payload.length < 4) return
        val a = payload.substring(0, 2).toInt(16)
        val b = payload.substring(2, 4).toInt(16)
        // Cálculo bitwise robusto: (A * 256 + B) / 4
        val rpm = ((a shl 8) or b) / 4
        listener.onDataParsed(ObdPid.RPM, rpm)
        log("Parsed RPM: $rpm")
    }
}