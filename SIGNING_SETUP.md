# Signing and release

## Stable release identity
- applicationId: `com.tempinbox.privateinbox`
- Release keystore type: `PKCS12`
- Alias: `temp-email-release`
- Certificate SHA-256: `F0:0F:62:89:BA:A1:4E:11:13:C5:50:11:01:32:60:1D:DA:CC:62:00:C4:0B:65:91:CD:A0:7A:DF:2F:CB:3E:DF`
- Valid through: 2056

## Environment variables
The signed release expects:
- `TEMP_EMAIL_KEYSTORE_PATH`
- `TEMP_EMAIL_KEYSTORE_PASSWORD`
- `TEMP_EMAIL_KEY_ALIAS`
- `TEMP_EMAIL_KEY_PASSWORD`

For the provided keystore:
- alias: `temp-email-release`
- store type: `PKCS12`

## Future updates
Every public update must keep the same applicationId and release key. Only increment versionCode/versionName and update the changelog.

Never commit the keystore, private passwords, or private key material to GitHub.
