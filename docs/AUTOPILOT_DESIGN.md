# PocketForge Autopilot safety contract

Autopilot is designed to make real repository changes without treating AI confidence as proof.

1. Never edit the user's green/default branch directly.
2. Create a dedicated `pocketforge/...` branch from a known commit.
3. Let the scout model choose only a small set of relevant text files to read.
4. Require a structured change manifest with full replacement contents.
5. Reject path traversal, secrets, signing material, generated build folders, and oversized edits.
6. Apply bounded changes only to the protected branch.
7. Dispatch Android CI and expose every stage in the chat work trace.
8. A compiler/test/lint/signature failure is authoritative. The repair agent gets the real logs and may make bounded repair attempts on the same branch.
9. Never auto-merge a failed or unverified branch.
10. Preserve a branch/commit checkpoint so the user can inspect or abandon the run.
