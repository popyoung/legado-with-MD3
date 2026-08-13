package io.legado.app.model

sealed interface ReadAloudPageChangeOrigin {
    data object User : ReadAloudPageChangeOrigin
    data object Programmatic : ReadAloudPageChangeOrigin
    data class Restore(val token: Long) : ReadAloudPageChangeOrigin
}
