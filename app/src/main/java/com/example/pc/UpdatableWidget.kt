package com.example.pc

/**
 * Интерфейс для виджетов, которые могут обновлять свое содержимое
 * на основе свежих данных с сервера.
 */
interface UpdatableWidget {
    fun updateData(stats: PCStats)
}
