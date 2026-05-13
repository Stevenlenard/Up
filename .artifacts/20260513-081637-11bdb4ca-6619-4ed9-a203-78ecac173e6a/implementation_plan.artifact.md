# Implementation Plan - Driver Dashboard Cleanup and Enhancement

This plan outlines the changes to the **Driver Dashboard** to remove the "Purok" list, make certain trip information fields editable, and optimize the layout for better space utilization.

## Proposed Changes

### 1. Layout Refactoring

#### [activity_driver_dashboard.xml](file:///C:/xampp/htdocs/Asia-repo1-main/app/src/main/res/layout/activity_driver_dashboard.xml)

- **ALISIN TULUYAN**: The entire `MaterialCardView` block containing "Purok 3", "Purok 4", etc. will be deleted from the XML file.
- **Trip Information Section**:
    - **Plate Number**: Clickable value (`tvPlateNumberValue`) -> Opens input dialog -> **Saves to Firebase**.
    - **Start Time**: Clickable value (`tvStartTimeValue`) -> Opens `TimePickerDialog` -> **Saves to Firebase**.
    - **Estimated End**: Clickable value (`tvEstimatedEndValue`) -> Opens `TimePickerDialog` -> **Saves to Firebase**.
- **Expansion**: The map height will be increased to `280dp`.

#### [DriverDashboardActivity.kt](file:///C:/xampp/htdocs/Asia-repo1-main/app/src/main/java/com/example/myapplication/DriverDashboardActivity.kt)

- **Firebase Sync**: Updates to Plate Number, Start Time, and Estimated End will be saved under `truck_locations/{truckId}/trip_info`.
- **Live Listeners**: The UI will listen for changes in the database to stay in sync.

## Verification Plan

### Automated Tests
- No specific automated tests exist for this UI, but I will check for layout errors using `analyze_file`.

### Manual Verification
- **Visual Inspection**: Use `render_compose_preview` (if applicable) or verify via screenshots if I could run the app. Since I cannot run the app, I will rely on code analysis and XML structure.
- **Logic Check**: Ensure `Truck Number` is indeed not clickable/editable in the code.
- **Logic Check**: Ensure `Plate Number`, `Start Time`, and `Estimated End` have listeners attached.

## User Review Required
- **Plate Number Input**: Should the plate number be saved to a database immediately, or is it just for the current session?
- **Time Format**: Should we stick to the 12-hour (AM/PM) format for time pickers?
