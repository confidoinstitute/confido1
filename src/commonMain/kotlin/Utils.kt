package tools.confido.utils

fun binRanges(min: Double, max: Double, bins: Int) : List<Pair<Double, Double>> {
    val binSize = (max - min) / bins
    return (0 until bins).map {min + it*binSize to min + (it+1)*binSize}
}

fun binBorders(min: Double, max: Double, bins: Int) : List<Double> {
    val binSize = (max - min) / bins
    return (0..bins).map {min + it*binSize}
}

val alnum = ('a'..'z').toList() + ('0'..'9').toList()
fun randomString(length: Int) =
    (1..length).map {
        alnum.random()
    }.joinToString()