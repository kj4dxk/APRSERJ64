# Versions and GitHub tags

APRSERJ64 follows Lynn's APRSISCE/32 convention: `YYYY.MM.DD.HHmm`.
For example, `2024.09.05.1714` means September 5, 2024 at 17:14.
APRSERJ64 release timestamps use America/New_York time, including daylight saving.
This is date-based versioning, not semantic versioning or a compatibility promise.

`version.txt` is the single source for the release identifier. Set it once when
preparing a release; rebuilding that source retains the same version. Never reuse
a published version for changed source. Use a later minute if necessary.

The identifier appears in About, startup logs, APRS-IS/CWOP identification,
version-query replies, and the portable EXE's Windows version metadata.
Windows may display numeric components without leading zeroes. The internal
jpackage image requires three components and uses `YY.month.(day*1440+hour*60+minute)`;
that packaging-only value is not the public version or GitHub tag.

For each published build:

1. Set `version.txt`, commit all source (including shared-core changes), and run checks.
2. Build the portable EXE from that commit.
3. Create an annotated Git tag exactly matching `version.txt` and push it.
4. Publish a GitHub Release under that same tag, attaching the matching EXE and manual.
5. Keep previous tags and release assets unchanged. Never move an existing release tag.

The original `portable-2026-11-22` release remains an historical release of the
earlier `0.8.0` numbering scheme. A version prepared in source is not a published
binary until its matching release assets are uploaded.
