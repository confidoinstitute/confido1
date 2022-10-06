package tools.confido.utils

operator fun Number.compareTo(b : Number): Int {
    if ((this is Int || this is Short || this is Long || this is Byte || this is Float || this is Double)
        && (b is Int || b    is Short || b    is Long || b    is Byte || b    is Float || b    is Double))
            return this.compareTo(b)
    else throw NotImplementedError()
}


val alnum = ('a'..'z').toList() + ('0'..'9').toList()
fun randomString(length: Int) =
    (1..length).map {
        alnum.random()
    }.joinToString("")

