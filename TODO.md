Persistence and data structure: [FŠ]
- Most data is still ephemeral
  - Questions load from db, but are not synchronized
- Mutability of objects
- How to handle app state

Authentication and rooms: [SE]
- Adam's feedback

Frontend missing important functionality:
- Group predictions do not work
  - How to show a graph? Modal?
- Error handling for comment post
- Resolutions do not exist
  - Requires to create a structure first [FŠ?]
- Answer space cannot be edited even if no prediction yet exists
  - Allow it when no predictions are made, in case of a race condition do what?
- Units for answer space
- Number of bins for a new question

Frontend features:
- Question deletion
- Comment moderation
- Navigation and user preferences
- Rounding of prediction values
- Prediction manager to aggregate predictions
- Question reveals how many people predicted

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