# Workflow
Never write or edit code without being explicitly told to do so! You may ask if you are allowed to make changes, but do not do so unless I explicitly confirm.

When committing and pushing changes to CLAUDE.md only, include `[skip ci]` in the commit message to avoid triggering CI.

# Code Quality
Never suppress warnings. Do not use `@SuppressWarnings` annotations or any equivalent suppression mechanism. Instead, fix the underlying issue.

The single sanctioned exception to this rule is `codec-crypto/spotbugs-exclude.xml`'s
`CIPHER_INTEGRITY` match on `JceDataKeyProvider.wrapCipher`: find-sec-bugs' `CipherWithNoIntegrityDetector`
does not recognize AES Key Wrap (RFC 3394, transform `AESWrap`) as integrity-protected, even
though it is (a 64-bit ICV verified on unwrap) — a documented gap in the detector's known-safe
list, not a real finding, and specs 005/006 mandate AES-KW as the wrap scheme for this provider.
See the comment in that file for the full analysis. Any other suppression remains forbidden.
