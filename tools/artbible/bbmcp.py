"""Klien MCP minimal untuk Blockbench MCP (HTTP, http://localhost:3000/bb-mcp).

    python tools/artbible/bbmcp.py tools                  -> daftar tool
    python tools/artbible/bbmcp.py call NAME '{"a":1}'    -> panggil tool, cetak hasil
    python tools/artbible/bbmcp.py eval FILE.js           -> jalankan JS di Blockbench (risky_eval)
"""
import json
import sys
import urllib.request

URL = "http://localhost:3000/bb-mcp"
_session = None
_id = 0


def _post(payload):
    global _session
    data = json.dumps(payload).encode()
    req = urllib.request.Request(URL, data=data, method="POST", headers={
        "Content-Type": "application/json", "Accept": "application/json, text/event-stream",
        **({"mcp-session-id": _session} if _session else {})})
    with urllib.request.urlopen(req, timeout=300) as r:
        sid = r.headers.get("mcp-session-id")
        if sid:
            _session = sid
        body = r.read().decode("utf-8", "replace")
        if not body.strip():
            return None
        if r.headers.get("content-type", "").startswith("text/event-stream"):
            out = None
            for line in body.splitlines():
                if line.startswith("data:"):
                    out = json.loads(line[5:].strip())
            return out
        return json.loads(body)


def rpc(method, params=None, notify=False):
    global _id
    payload = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        payload["params"] = params
    if not notify:
        _id += 1
        payload["id"] = _id
    return _post(payload)


def connect():
    rpc("initialize", {"protocolVersion": "2025-03-26", "capabilities": {},
                       "clientInfo": {"name": "liarsbar-tools", "version": "1"}})
    rpc("notifications/initialized", notify=True)


def call(name, args=None):
    r = rpc("tools/call", {"name": name, "arguments": args or {}})
    if r is None:
        return None
    if "error" in r:
        raise RuntimeError(json.dumps(r["error"]))
    return r["result"]


def text(result):
    out = []
    for c in (result or {}).get("content", []):
        if c.get("type") == "text":
            out.append(c["text"])
        elif c.get("type") == "image":
            out.append("<image %s %d bytes>" % (c.get("mimeType"), len(c.get("data", ""))))
    return "\n".join(out)


def save_images(result, prefix):
    import base64
    paths = []
    for i, c in enumerate((result or {}).get("content", [])):
        if c.get("type") == "image":
            p = "%s_%d.png" % (prefix, i)
            open(p, "wb").write(base64.b64decode(c["data"]))
            paths.append(p)
    return paths


if __name__ == "__main__":
    connect()
    cmd = sys.argv[1]
    if cmd == "tools":
        r = rpc("tools/list")
        for t in r["result"]["tools"]:
            print(t["name"], "::", (t.get("description") or "").split("\n")[0][:150])
    elif cmd == "schema":
        r = rpc("tools/list")
        for t in r["result"]["tools"]:
            if t["name"] == sys.argv[2]:
                print(json.dumps(t, indent=1))
    elif cmd == "call":
        res = call(sys.argv[2], json.loads(sys.argv[3]) if len(sys.argv) > 3 else {})
        print(text(res))
        if len(sys.argv) > 4:
            print(save_images(res, sys.argv[4]))
    elif cmd == "eval":
        code = open(sys.argv[2], encoding="utf-8").read()
        res = call("risky_eval", {"code": code})
        print(text(res))
