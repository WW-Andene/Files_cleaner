package com.filecleaner.app.data

enum class ScanPhase { INDEXING, DUPLICATES, ANALYZING, JUNK }

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val filesFound: Int, val phase: ScanPhase = ScanPhase.INDEXING) : ScanState()
    object Done : ScanState()
    object Cancelled : ScanState()
    data class Error(val message: String) : ScanState()
}
