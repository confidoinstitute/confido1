package rooms

enum class RoomPermission {
    VIEW_QUESTIONS,
    SUBMIT_PREDICTION,
    ADD_QUESTION,
    SUGGEST_QUESTION,
    VIEW_HIDDEN_QUESTIONS,
    /**
     * See all group and individual predictions regardless of question setting.
     */
    VIEW_ALL_PREDICTIONS,
    MANAGE_QUESTIONS,
    MANAGE_MEMBERS,
}