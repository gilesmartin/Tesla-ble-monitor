# ScreenMate public wake gateway

This is an **opt-in, wake-only** ingress. It does not make an unpowered/offline
head unit reachable. Reliable availability requires ScreenMate power, Internet,
Bluetooth permission/proximity, an existing vehicle-authorized key, Android
background allowance and a valid dedicated Cloudflare Tunnel configuration.
Force-stop, power removal, head-unit sleep and vehicle key rejection still apply.

## Request contract

```
POST https://<chosen-wake-hostname>/v1/wake
Authorization: Bearer <separate-high-entropy-wake-bearer>
Content-Type: application/json

{}
```

The body must be empty or an empty JSON object. It cannot choose a VIN, operation,
arguments, shell command or origin. Unknown routes are not forwarded. This origin
is bound to `127.0.0.1:8787`, **not** the telemetry API or a wildcard interface.
Headers/body/read duration are bounded; duplicate authentication, transfer
encoding and ambiguous framing are rejected. Repeat requests are throttled.
`202` means admitted/queued, **not** that the vehicle has physically woken.
`409` means another wake is pending; `429` means repeat protection; `503` means
local admission/readiness is unavailable. No public status, telemetry or control
route is provided.

The public path is Cloudflare -> ScreenMate connector -> loopback wake origin ->
existing BLE-owner looper -> the fixed native `wake` operation. It does not call
MQTT, Home Assistant, a home server, Tailscale addresses or the generic command
API. Transport/readiness and vehicle acknowledgement are separate facts.

## Configuration and secrets

Open **Public wake / Cloudflare Tunnel** from app settings. Default is disabled.
Configure a dedicated remotely managed Cloudflare Tunnel token and a **different**
wake bearer (the UI can generate one with 256 random bits). Blank entries preserve
the existing value. Credentials share a separately encrypted AES-GCM envelope in
private preferences, protected by their own Android Keystore alias; existing API,
MQTT and vehicle keys are neither copied nor replaced. Credential inputs are not
saved as view state or autofill; this screen prevents screenshots.

The connector gets its token through a private `0600` temporary file, never an
argument, environment variable or diagnostic log. Child output is discarded.
Only sanitized connector/wake outcome strings appear in the local UI.

The battery button explicitly requests the app's Android power exemption. The app
does **not** silently change system power or VPN settings. The local status shows
whether Android reports the exemption. An exemption cannot defeat physical power
loss or a vendor head-unit shutdown policy.

## Cloudflare side

Use a **dedicated** ScreenMate tunnel; do not share the home-server connector
identity or change the existing production hostname while commissioning it.
The selected hostname must route only to `http://127.0.0.1:8787`, with a final
not-found rule. Never route it to the telemetry port or an administrative listener.
Keep credentials out of source, CI, shell arguments, screenshots and chat.
The tunnel and origin are independent of the caller's network and Tailscale,
provided ordinary Internet/DNS/TLS connectivity works on ScreenMate.

Do not cut over an existing public hostname until the new connector is connected,
Android power/recovery is verified, and harmless external negative/invalid-body
requests have proven authentication and routing. Preserve the former route for
rollback. Changes to the selected hostname/account require the owner's decision.

## BLE owner and lifecycle

An accepted request leases the existing BLE connection workflow for a bounded
period if needed. It does not persist a new desired-connection preference, generate
keys, enroll a controller, or create a second GATT/native client. Charge, driving,
legacy commands, pairing and enrollment cannot overlap the public wake owner.
A busy owner is awaited within the deadline; timeout/cancellation releases the
lease. Explicit disconnect/stop cancels an admitted request. Native results return
to the owner looper; completion cleanup is deferred out of native callbacks.
A command acknowledgement is not independent proof of fresh vehicle state.

When enabled, the listener/connector resume through normal foreground-service,
sticky restart, boot and package-replacement paths. Startup/configuration does not
invoke wake. Explicit service stop disables the public gateway. The child has
single-owner locking, bounded restart backoff and cleanup on normal shutdown.
Crash/process-group, Doze and vendor power behavior require **real Android** tests;
host process simulations do not prove them.

## Android connector supply chain

`tools/fetch_cloudflared.py` now **builds Android/Bionic**, not the upstream static
Linux executable. It uses the immutable upstream commit, digest-pinned Go archives,
fixed NDK/API and `go.sum`-verified modules in `cloudflared-android.lock.json`.
The resulting ARM64 PIE must name `/system/bin/linker64`. The native library is
packaged as `libcloudflared.so` without stripping/modification and verified against
the build manifest embedded in the signed APK. The manifest is not user input.
Upstream/Go/module license notices accompany the APK. Build before provisioning
CI signing credentials. No Cloudflare or vehicle credentials are needed to build.
Other CPU ABIs cannot use this ARM64 connector; failure is closed.

## Acceptance and rollback

1. Run all source/actual-loopback tests, native fixtures, Android lint and build.
2. Verify the exact APK package/version, permanent signer, embedded Android ELF,
   connector digest/manifest and license notices. A local debug signer is **not**
   a valid replacement for the permanently signed installed application.
3. Install only the verified compatible artifact with data-preserving replacement;
   read back the installed hash, UID/user state and foreground service. Never
   uninstall, clear data, copy vehicle keys or overlap uncertain install sessions.
4. Confirm the packaged connector works under the **app UID**, not just adb root.
   Confirm real tunnel readiness, negative/invalid-body public responses, power
   exemption, network recovery and actual client/device VPN-off paths. Do not claim
   these are verified from source tests or `--version` alone.
5. A live physical wake test needs explicit authorization. Correlate its native
   acknowledgement with fresh vehicle state; a queued response is insufficient.
6. To roll back the ingress, disable the gateway and restore the old Cloudflare
   route. Existing DiPlay enrollment, telemetry API/MQTT settings, key material and
   saved BLE/poll preferences remain unchanged. Any binary downgrade must use the
   preserved compatible signer and the guarded installation/readback procedure.
