# Development guide

Use the addyosmani/agent-skills workflow as applicable: small implementation slices, meaningful tests, independent review, documented evidence.

- Target: 京东秒送骑士, package `com.dada.mobile.android`; verify changes against vendor or official store metadata.
- User decides each order. One physical trigger permits at most one simulated swipe. Never add automatic order selection, refresh loops, retries, hidden operation or detection evasion.
- Production debug/release must not allow runtime replacement of the target package. A separate test-only build can target an explicitly named, separately packaged fixture.
- Synthetic demo data only. Do not commit original order screenshots, customer details, accounts, keys, APKs or device logs.
- Build with `./gradlew assembleDebug assembleFixtureCheck assembleFixtureCheckAndroidTest lintDebug` and run the documented device tests. Keep actual source paths and credentials out of public documentation.
- Report simulated controls, cross-app fixture results, and actual rider-app acceptance separately. Never equate `dispatchGesture.onCompleted` with successful order acceptance.
