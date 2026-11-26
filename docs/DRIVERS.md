# Router drivers

A driver is a small JSON "recipe" that tells Router Guard how to log in to a router, read its device list,
and block/unblock a device. The app only interprets recipes; it never runs code from them, and recipe paths
must be relative to the router (`/...`), so a driver can only talk to your own router.

Drivers live in the phone's private `drivers` folder. Ones shipped in `app/src/main/assets/drivers/` are copied
there the first time they are needed. `Settings > Drivers > Install a driver file` adds more.

## Shape

```json
{
  "schema": 1, "id": "my-router", "version": 1, "name": "My router", "status": "experimental",
  "login":   [ {"set": {"pw64": "b64({password})"}},
               {"get": "/", "extract": {"tok": "var tok='([^']+)'"}},
               {"post": "/login", "form": {"pw": "{pw64}", "t": "{tok}"}, "expect": "^OK", "error": "Wrong password"} ],
  "logout":  [ {"get": "/logout"} ],
  "devices": { "steps": [ {"get": "/devices"} ],
               "parse": {"json": "[*]", "fields": {"mac": "mac", "ip": "ip", "name": "host", "on": "on"},
                         "lower": ["mac"], "online": {"field": "on", "equals": "1"}} },
  "blocked": { "steps": [ {"get": "/list"} ],
               "parse": {"regex": "ROW\\(\"([^\"]+)\",\"([^\"]+)\"\\)", "fields": {"mac": 1, "id": 2}} },
  "block":   [ {"post": "/block", "body": "mac={mac}&ssid={ssid}", "expect": "ok"} ],
  "unblock": { "each": "blocked", "steps": [ {"post": "/del", "body": "id={id}", "expect": "ok"} ] }
}
```

Variables: `{host} {user} {password}` plus anything saved by `extract`/`set`, and `{mac} {MAC} {ssid} {id}` for
block/unblock. Functions in `set`: `md5() sha256() b64() lower() upper()`. Step keys: `get`/`post`, `form`, `body`,
`contentType`, `headers`, `cookie`, `ajax`, `unescapeHex`, `extract`, `expect`, `error`, `save`.
Device parsers support `regex` (with `group`, `split: "quoted"`) or `json` paths.

The engine is tested on the JVM against a fake router (login token, cookies, device list, block, unblock,
and rejection of unsafe recipes).
