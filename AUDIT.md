# Publication audit — 25 October 2026

## Scope

A selected source snapshot and the original supplied APK. No private Git history, discussion transcript, runtime storage, contact list, audio recording, screenshot, signing key, ADB identity, machine configuration or private build log is imported.

## Completed checks

- Supplied and recovered APK SHA-256 hashes match exactly; see SHA256SUMS.
- APK signatures verify with Android apksigner. The certificate identifies Android Debug; this is a development-signing limitation.
- Source scan and two APK inspections check credential patterns, private paths, network addresses, private asset types and identifying strings. UTF-16 compiled resources are included in the second scan.
- Conscrypt native libraries contain upstream build-machine paths. Their complete binary hashes match the original Conscrypt 2.5.3 dependency; the scan exception is restricted to these exact hashes, not arbitrary libraries.
- Test-only fake API key and private-key-header strings exercise rejection. They are explicit nonfunctional fixtures, not live secrets.
- Loopback 127.0.0.1 is required for local activation, not a private remote host.
- Original application source is preserved. Build files are newly written for portable SDK configuration; Java 17 compile settings are explicit.

## Validation results

Release compilation, all 72 JUnit tests and lint complete. Lint reports zero errors and 74 warnings; see docs/lint-findings.json.

This is a publication review, not an independent security audit. No new on-device call was made. Scan success cannot prove the absence of all sensitive content, vulnerabilities or unsafe runtime behavior.

## Recheck

```sh
python3 scripts/privacy_scan.py
python3 scripts/privacy_scan.py --apk downloads/appel-ai-0.16.apk
cd downloads && sha256sum -c SHA256SUMS
```

The source scan uses Git's tracked-file inventory. Review every staged file before publication; generated build directories remain ignored.

## Additional account/contact review

Tracked source files were searched for embedded account/project identifiers, email addresses and phone literals. The remaining phone literals are synthetic test/diagnostic placeholders, not imported contacts. No personal OpenAI account, organization/project identifier or actual credential was detected. Public OpenAI endpoints and code that accepts each user's own credential remain intentionally present. Test names are fictional examples; author attribution is intentional.

Original source line endings and trailing whitespace were normalized for publication; runtime logic was not changed.
