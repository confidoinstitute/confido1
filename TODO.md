Persistence and data structure:
- Most data is still ephemeral
  - Questions load from db, but are not synchronized
- Mutability of objects
- How to handle app state

Frontend missing important functionality:
- WebSocket is not reloaded when it is closed
- Websocket URL based on real server location
- Group predictions do not work
  - How to show a graph? Modal?
- Error handling for comment post
- Resolutions do not exist
- Answer space cannot be edited even if no prediction yet exists

Frontend features:
- Question deletion
- Comment moderation
- Navigation and user preferences
- Question list to be routed
- Rounding of prediction values
- Prediction manager to aggregate predictions

Structure:
- Distribution is general, needs answer space and can be specialized with params
  - Can be converted to string (E X, Var X?) or graph (distribution)
- User preferences to app state
- Binning of numeric answer space
  - Unbounded numeric answer space?
  - How to set precision?

Source code cleanup:
- Rename roots (frontend, backend, common)
- Unify directory structure
- Module split?