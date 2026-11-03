package tools.confido.utils

import tools.confido.refs.Ref
import tools.confido.refs.ServerImmediateDerefEntity
import tools.confido.refs.deref

// Bulk-deref helpers over the resident stores — server-only (client code reads
// entities from topic payloads; see PERSISTENCE_SYNC_REDESIGN.md client endgame).
inline fun <reified T: ServerImmediateDerefEntity> Iterable<Ref<T>>.mapDeref() =  map { it.deref() }.filterNotNull()
inline fun <reified T: ServerImmediateDerefEntity, R> Iterable<Ref<T>>.mapDeref(body: (T)->R) =  mapDeref().map(body)
inline fun <reified T: ServerImmediateDerefEntity> Iterable<Ref<T>>.forEachDeref(body: (T)->Unit) = forEach { it.deref()?.let { body(it) } }
