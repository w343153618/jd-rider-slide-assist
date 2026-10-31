# Development guide

## Mandatory Addy Osmani workflow

User rule, confirmed 2026-10-31: **every code change in this project must use `addyosmani/agent-skills`. This is mandatory, including small fixes, tests, scripts, build changes and changes made by delegated agents.**

- Before editing code, select and read the relevant Addy skills. Use `using-agent-skills` when routing is unclear; load only skills needed for the work.
- Implement with `incremental-implementation`, use `test-driven-development` for behavioral changes, and use `debugging-and-error-recovery` when investigating a failure. Apply the appropriate UI, security, performance or source-documentation skill when its scope is involved.
- Every code change must receive `code-review-and-quality` review and follow `git-workflow-and-versioning`. Record meaningful verification and material limitations in project evidence; a successful build alone does not prove interaction behavior.
- Tell the user which relevant skills are being applied. Pass this requirement to every delegated agent. Never bypass these steps because a change seems small or urgent.
- If a required skill cannot be found, report the concrete gap instead of silently claiming the workflow was used. User instructions and platform safety requirements remain higher priority.

## Product and verification boundaries

- Target: 京东秒送骑士, package `com.dada.mobile.android`; verify changes against vendor or official store metadata.
- User decides each order. One physical trigger permits at most one simulated swipe. Never add automatic order selection, refresh loops, retries, hidden operation or detection evasion.
- Production debug/release must not allow runtime replacement of the target package. A separate test-only build can target an explicitly named, separately packaged fixture.
- Synthetic demo data only. Do not commit original order screenshots, customer details, accounts, keys, APKs or device logs.
- Build with `./gradlew assembleDebug assembleFixtureCheck assembleFixtureCheckAndroidTest lintDebug` and run the documented device tests. Keep actual source paths and credentials out of public documentation.
- Run `tests/run-unit-tests.sh` for the volume-key state machine and supported timing values. Volume shortcuts only arm/disarm assistance; they must never dispatch a swipe themselves. Preserve the system key event stream and target/lock guards.
- Report simulated controls, cross-app fixture results, and actual rider-app acceptance separately. Never equate `dispatchGesture.onCompleted` with successful order acceptance.
- White-handle hit testing and exact original DOWN coordinates are shared via SliderGeometry. State-bubble toggles require triple taps; dragging/cancelling resets the tap sequence.
- Measurements are explicitly enabled, local and bounded. Keep manual timing separate from auxiliary animation/system callbacks. Preserve raw device values, missing-data labels and export limits; never fabricate physical pressure, electrical current, hardware latency or server acceptance measurements.
