package com.eduardo.nunes.drt.core.bluetooth

// Enum tem custo zero de alocação em tempo de execução
enum class ObdPid {
    SPEED,
    RPM,
    // Facilmente escalável para o futuro:
    // COOLANT_TEMP,
    // ENGINE_LOAD
}

// A interface SAM que servirá como nosso contrato
fun interface ObdDataListener {
    fun onDataParsed(pid: ObdPid, value: Int)
}