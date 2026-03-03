# Device Smoke Test Summary

- **Date:** 2026-03-02
- **Branch:** `codex/device-smoke-evidence`

## Device Info

| Field         | Value                  |
|---------------|------------------------|
| Serial        | 49281FDAQ0011J         |
| Connection    | USB (usb:1-1)          |
| Product       | tokay                  |
| Model         | Pixel_9                |
| Device        | tokay                  |
| Transport ID  | 1                      |

## Port Forwarding Status

| Port  | Direction | Status  |
|-------|-----------|---------|
| 3000  | reverse   | SUCCESS |
| 54321 | reverse   | SUCCESS |

## Log Capture Results

- **Logcat buffer cleared:** YES
- **Capture mode:** `adb logcat -d` (dump current buffer)
- **Filter pattern:** `TXN|HTTP|TICKET|PAYMENT|PRINT|BROTHER|Correlation`
- **Lines captured:** 0
- **Status:** Pending manual execution

The log buffer was intentionally cleared before capture. No matching log lines
were found because the manual app flow has not yet been executed on the device.

## Manual Flow Required

The following end-to-end flow must be executed manually on the Pixel 9 device
to populate `device-smoke.log` with real transaction evidence:

1. **Login** -- Authenticate into the LCX app
2. **Create Ticket** -- Create a new ticket/transaction
3. **Charge** -- Process a payment (card or cash)
4. **Print** -- Print receipt via Brother printer
5. **Persist** -- Verify transaction persistence

After executing the flow, re-run the logcat capture:

```bash
adb logcat -d | grep -E "TXN|HTTP|TICKET|PAYMENT|PRINT|BROTHER|Correlation" > docs/evidence/20260302/device-smoke.log
```

## Errors

- None. Device is connected, ADB is responsive, port forwarding is active.
