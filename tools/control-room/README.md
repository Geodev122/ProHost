# IG Control Room (multi-brand console)

Source of the claude.ai artifact "IG Control Room" (https://claude.ai/artifact/DRoE6CSwogWWD9GzG3tntn). It edits the Google Sheet
"ProHost IG Automations" through the Sheets connector; the backend (`functions/src/instagram`) mirrors the sheet every minute.

- Build: `node tools/control-room/build.js` → `dist/control-room.html` (then publish that file with the Artifact tool to the same URL;
  capabilities: `downloads`, `mcp` = Google Sheets `get_values` + `update_values`).
- Test: `cd functions && npm run build && cd .. && node tools/control-room/test/console.test.js` (Chromium against an in-memory sheet).
- `src/engine.js` is a JS copy of `functions/src/instagram/engine.ts` + `rulesSheet.ts` (keep identical; cases in `engine.test.ts`).
- One spreadsheet, every brand: ProHost keeps the original tab names, other brands' tabs are prefixed (`acme_Rules`). Registry tabs:
  Brands, Platforms, Secrets. Per-brand tabs: Rules, Log, Contacts, Status, Knowledge, Learn, Leads, Profile, Buttons, Playbooks, Sources,
  KnowledgeDraft.
- Secrets typed in the console go to the Secrets tab; the server checks them with Meta, stores them server-side and blanks the cell.
