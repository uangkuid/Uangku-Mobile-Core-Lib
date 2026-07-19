# ⚠️ Obsolete — see the backend contract instead

This file previously documented the **old** Uangku backend encryption model (AES-256-CBC,
RSA-2048, bcrypt secret key, server-side key derivation). That model has been **superseded in
full** by the Zero-Knowledge / Two-Secret-Key-Derivation (2SKD) design.

**The current interop source of truth is `docs/encryption.md` in the backend repo (`Uangku-BE`).**
It defines the shipped contract that `libs/core_crypto` implements:

- 2SKD: `PBKDF2-HMAC-SHA256(600k)` + `HKDF-SHA256` + XOR → `unlockKey`, then `authKey`
- AES-256-GCM container `ver(0x02)‖iv(12)‖ct‖tag(16)`, base64
- RSA-OAEP-SHA256, 4096-bit (`public_key` = `base64(PEM SPKI)`, private key = PKCS#8 PEM)
- Hybrid envelope `{v, ek, ct}` for financial fields and family key wrapping

For the client-side implementation notes, see [`docs/core_crypto.md`](docs/core_crypto.md).

Nothing in the old model (AES-CBC, RSA-2048, server-held keys) is still in use. Do not rely on
this file for anything.
