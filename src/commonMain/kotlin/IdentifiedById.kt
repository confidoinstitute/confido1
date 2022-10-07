package tools.confido.eqid

interface IdentifiedById<K> {
    val id: K

    infix fun eqid(other: IdentifiedById<K>) =
        this.id == other.id
}

fun <K, T: IdentifiedById<K>> List<T>.indexOfById(what: T) = this.indexOfFirst { it eqid what }
fun <K, T: IdentifiedById<K>> List<T>.findById(what: T) = this.find { it eqid what }

fun <K, T: IdentifiedById<K>> MutableList<T>.insertById(what: T, replace: Boolean = false) {
    val index = this.indexOfById(what)
    if (index == -1) {
        this.add(what)
    } else if (!replace) {
        this[index] = what
    }
}
fun <K, T: IdentifiedById<K>> MutableList<T>.removeById(what: T) = this.removeAll { it eqid what }

fun <K, T: IdentifiedById<K>, V> Map<K, V>.get(what: T) = this[what.id]
fun <K, T: IdentifiedById<K>> MutableMap<K, T>.insert(what: T) = this.set(what.id, what)
fun <K, T: IdentifiedById<K>> MutableMap<K, T>.remove(what: T) = this.remove(what.id)
