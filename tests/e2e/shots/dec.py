import base64, json, sys
src, out = sys.argv[1], sys.argv[2]
d = json.load(open(src))
data = d.get("data") or d.get("result", {}).get("data")
open(out, "wb").write(base64.b64decode(data))
print(out)
