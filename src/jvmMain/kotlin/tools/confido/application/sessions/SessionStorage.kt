package tools.confido.application.sessions

interface SessionStorage<T> {
    /**
     * Stores [data] for a session named [name] in the storage.
     */
    fun store(name: String, data: T)
    /**
     * Loads data for a session named [name] from the storage.
     */
    fun load(name: String): T?
    /**
     * Loads data for a session named [name] from the storage.
     *
     * If the data is not found, a default value is constructed through [defaultValue], saved and then returned.
     */
    fun loadOrStore(name: String, defaultValue: () -> T): T
    fun clear(name: String)
}

