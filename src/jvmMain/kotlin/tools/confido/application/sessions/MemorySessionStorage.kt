package tools.confido.application.sessions

import java.util.concurrent.ConcurrentHashMap

class MemorySessionStorage<T> : SessionStorage<T> {
    private val storage = ConcurrentHashMap<String, T>()

    override fun store(name: String, data: T) {
        storage[name] = data
    }

    override fun load(name: String): T? {
        return storage.getOrDefault(name, null)
    }

    override fun loadOrStore(name: String, defaultValue: () -> T): T {
        return storage.getOrPut(name, defaultValue)
    }

    override fun clear(name: String) {
        storage.remove(name)
    }
}