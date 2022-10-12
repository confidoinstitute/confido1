Bugs:
- Invitation link creates duplicate memberships

Frontend missing important functionality:
- Resolutions do not exist
- Answer space cannot be edited even if no prediction yet exists
  - Send number of predictors
  - Allow it when no predictions are made, in case of a race condition do what?
- If user is via invitation link, allow them to be invited directly (and change their link via to null)
- Feedback

Frontend features:
- Navigation and user preferences
- Prediction manager to aggregate predictions
- Question reveals how many people predicted

Source code cleanup:
- Rename roots (frontend, backend, common)
- Unify directory structure
- Module split?

Perhaps:
- Presenter mode