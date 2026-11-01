# Mood trend periods: next BE + FE PR

This is follow-up product work. The current backend refactor keeps the existing
`GET /stats/mood-trend?period=week` behavior and response unchanged.

## Current behavior

- `week` is the only accepted period and is the default.
- The backend uses the current date in `Asia/Ho_Chi_Minh` and queries the calendar
  week from Monday through Sunday.
- Each response point represents one day with a mood entry and contains `date`,
  `averageScore`, and `entryCount`. Days without mood data are omitted.
- The dashboard uses this weekly trend to calculate its weekly mood summary.

## Proposed scope for the next BE + FE PR

- Let users choose `week` or `month`; keep `week` as the default.
- Add an optional reference date so users can navigate to earlier weeks or months.
  Omission means today's date in `Asia/Ho_Chi_Minh`.
- Define `week` as a Monday-to-Sunday calendar week and `month` as a calendar
  month containing the reference date. Keep one response point per day.
- Keep the current response fields and return only days with mood data. The FE
  fills missing dates as gaps when rendering the selected calendar period.
- Update API documentation, BE boundary tests, and FE navigation and chart tests
  together. Confirm the query parameter name and date format in that PR.
