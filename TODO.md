- When you use an invite link and enter email of existing user, it should redirect
  you to login with prefilled email or similar
- Prepare deploy

Frontend missing important functionality:
- Answer space cannot be edited even if no prediction yet exists
  - Send number of predictors
  - Allow it when no predictions are made, in case of a race condition do what?

Frontend features:
- Navigation and user preferences
- Prediction manager to aggregate predictions
- Question reveals how many people predicted

Source code cleanup:
- Rename roots (frontend, backend, common)
- Unify directory structure
- Module split?
- Assign entity IDs in constructor, not during insert

Perhaps:
- Presenter mode
