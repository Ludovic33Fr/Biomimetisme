from flask import Flask, request, jsonify
import subprocess, shlex, time, os

app = Flask(__name__)

@app.route("/")
def index():
    return jsonify(ok=True, message="Mimosa demo webapp")

@app.route("/search")
def search():
    # This endpoint is intentionally simple — the proxy sits in front to block SQLi-like URIs.
    q = request.args.get("q", "")
    return jsonify(ok=True, q=q)

@app.route("/spawn")
def spawn():
    # Simulate an RCE that spawns a shell; Falco should kill it immediately.
    cmd = request.args.get("cmd", "id")
    try:
        # Use /bin/sh -c to make a real shell child process
        proc = subprocess.Popen(["/bin/sh", "-c", cmd], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        # Give Falco a (very) brief window to see/kill it
        try:
            out, err = proc.communicate(timeout=2)
            rc = proc.returncode
        except subprocess.TimeoutExpired:
            # Process is likely still running until Falco kills it
            proc.kill()
            out, err = b"", b"Killed by timeout (Falco likely intervened)"
            rc = -9
        return jsonify(ok=(rc==0), returncode=rc, stdout=out.decode(errors="ignore"), stderr=err.decode(errors="ignore"))
    except Exception as e:
        return jsonify(ok=False, error=str(e)), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
