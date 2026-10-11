package io.github.parthibdebnath.openplayer.data.db

/**
 * Below SQLite's bind-variable ceiling. An `IN (:list)` query binds one variable per element, and
 * the SQLite shipped up to Android 11 caps that at 999 - so a query over a large playlist's ids
 * threw outright on those devices.
 */
const val SQL_VARIABLE_CHUNK = 900

/** Runs [query] over [items] in chunks small enough for any SQLite version, concatenating results. */
suspend fun <T, R> chunkedQuery(items: List<T>, query: suspend (List<T>) -> List<R>): List<R> =
    if (items.size <= SQL_VARIABLE_CHUNK) {
        query(items)
    } else {
        items.chunked(SQL_VARIABLE_CHUNK).flatMap { query(it) }
    }

/** Runs [statement] over [items] in chunks small enough for any SQLite version. */
suspend fun <T> chunkedExecute(items: List<T>, statement: suspend (List<T>) -> Unit) {
    items.chunked(SQL_VARIABLE_CHUNK).forEach { statement(it) }
}
