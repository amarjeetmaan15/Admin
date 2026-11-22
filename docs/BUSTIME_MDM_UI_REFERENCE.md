# Bustime MDM UI Reference — Admin App Port

Source of truth: `amarjeetmaan3/Bustime`, `MdmPanelActivity.kt` and `ui/common/StopListEditor.kt`.
Target: `amarjeetmaan15/Admin`. This document captures the source UI contract for the native Admin app. It does not authorize changing Supabase schemas, publishing semantics, authentication, or sync logic.

## Main MDM screen
Keep the source order and labels:
1. Route List
2. Bus Stop List
3. Recycle Bin
4. Review Center
5. Add Route
6. Correction Route
7. Add Bus Stop
8. Back

The target app may retain its required owner sign-in, language selector, and Sync Now control, but the seven management destinations and their order must match the source. Back from the home screen exits the MDM screen; Back inside a destination returns to the previous MDM page.

## Shared page shell
Each destination is a full-page screen, not a modal. Show a prominent title, a short explanatory subtitle where applicable, a visible Back action, and vertically scrollable content. Preserve back-stack behavior for nested details/editors.

## Route List
- Search field at top and live filtered result count.
- Each route is a separate selectable rounded row/card; show From → To and useful route metadata.
- Selecting a route opens a full route-detail page.
- Detail actions: Correct this route, Copy Route, Return Route, Delete route.
- Copy and Return create a new route with blank times; Return reverses the stop order.
- Delete must use the existing target app publish/recycle implementation and confirmation.

## Bus Stop List and details
- Search field, live count, selectable rounded rows.
- Selecting a stop opens a full-page details screen showing English, Hindi, Punjabi names and Place ID.
- Detail actions: Correct this stop and Delete this stop.
- Do not permit deleting a stop referenced by a route; show the blocking route(s).

## Review Center
- One shared queue containing Route Requests, Route Corrections, Add Your Bus / Book a Bus, and Feedback.
- Horizontal status filter chips with counts: Pending, Seen / Reviewed, Approved, Rejected, Deleted.
- Show only the selected status. Refresh reloads the queue.
- Each request is a rounded card with a clear heading, submitted date, request category, status, and all supplied details.
- Route and correction cards open the full-page route editor through Review / Edit Route.
- Bus requests open a full-page request editor through Review Bus Request.
- Feedback is read-only; it can only be marked Seen or Unseen.
- Other request actions: Approve, Reject, Move to Pending, Delete (soft-delete), and Restore for deleted requests.
- Keep action availability consistent with the request's current status.

## Add Route / Correction Route
Both use the same full-page editor and the same stop-list component.
- Intro text explains that known times may be entered and at least one stop must have a time.
- A vertical editable list of stop rows. Each row has: numbered endpoint/middle badge, autocomplete stop name, time chip/time picker, inline + (insert a stop below), and ⋮ menu.
- ⋮ menu: Move Up, Move Down, Remove Stop. Prevent removing below the configured minimum.
- Add Start button above the list; Add stop at end below it.
- Suggestions exclude stops already selected in other rows.
- Detect duplicate stops, allowing only a circular route's first/last endpoint repetition.
- Optional Service / bus name field.
- Preview route opens a full-page passenger-style route detail preview, not a toast or plain text dialog.
- Preview has Back to edit and Publish route actions.
- Preview uses the passenger route-detail layout and stop-row visual treatment, with distinct start, middle, and destination markers.
- Keep existing target payload, validation, publish and sync logic; port presentation/interaction only.

## Add Bus Stop / Correction Bus Stop
- Full-page form with type selector and separate English, Hindi, Punjabi name fields.
- Type options: Bus Stand, Village, City, Other Place (map to the target model's supported values without schema changes).
- Correction pre-fills existing values. Blank translations must preserve existing translations.
- Preview and Publish actions.
- Require at least one non-empty name.

## Recycle Bin
- Full-page list of deleted routes and stops.
- Each entry shows readable entity name, type, entity ID, and deletion date.
- Restore and Permanent Delete actions.
- Permanent Delete requires a confirmation dialog explaining recovery copy is removed permanently.
- Empty state says Recycle Bin is empty.

## Visual treatment
Use consistent spacing, rounded bordered cards, clear hierarchy, compact secondary metadata, full-width primary actions, and readable status labels. Search/filter bars should remain usable on narrow phones. All screens must scroll on small devices.

## Porting boundaries / acceptance checks
- Do not copy old Bustime authentication or its legacy password-based network calls. Admin must continue using the existing Supabase Auth session and owner UID authorization.
- Do not copy old repository/data-layer classes or change table/RPC/Edge Function contracts.
- Do not alter the target app's existing Supabase publish, sync, local cache, revision, or recycle implementations while matching UI.
- Verify navigation, Android back, search filtering, all five Review Center status filters, route editor row actions, route preview, bus-stop form, and recycle actions.
- Build the APK after the UI port and fix compile errors before declaring completion.
