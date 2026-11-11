"""Exercise a locally loaded image using isolated, disposable test containers.

No env files, host ports, or existing local/test stacks are used. Requires Python
3 and Docker. All credentials below are synthetic fixtures, never hosted secrets.
"""

import argparse
import json
import subprocess
import time
import uuid


def docker(*args, check=True):
    result = subprocess.run(
        ["docker", *args], capture_output=True, text=True, timeout=180
    )
    if check and result.returncode:
        raise RuntimeError(f"docker {args[0]} failed: {result.stderr.strip()}")
    return result


def wait_for(label, probe, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if probe():
            print(f"PASS: {label}", flush=True)
            return
        time.sleep(2)
    raise RuntimeError(f"Timed out waiting for {label}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", help="Already built/loaded backend image")
    args = parser.parse_args()
    prefix = "moodly-smoke-" + uuid.uuid4().hex[:12]
    containers = []

    def start(service, image, *command, options=()):
        name = prefix + "-" + service
        # Record before starting so cleanup also handles a partially failed run.
        containers.append(name)
        docker("run", "-d", "--name", name, "--network", prefix,
               "--network-alias", service, *options, image, *command)
        return name

    try:
        docker("network", "create", prefix)
        mongo = start("mongodb", "mongo:8.3.7", "mongod", "--replSet", "rs0", "--bind_ip_all",
                      options=("--tmpfs", "/data/db", "--tmpfs", "/data/configdb"))
        wait_for("MongoDB ping", lambda: docker(
            "exec", mongo, "mongosh", "--quiet", "--eval",
            "quit(db.adminCommand({ping:1}).ok ? 0 : 1)", check=False
        ).returncode == 0)
        docker("exec", mongo, "mongosh", "--quiet", "--eval",
               'rs.initiate({_id:"rs0",members:[{_id:0,host:"mongodb:27017"}]})')
        wait_for("MongoDB writable PRIMARY", lambda: docker(
            "exec", mongo, "mongosh", "--quiet", "--eval",
            "quit(db.hello().isWritablePrimary ? 0 : 1)", check=False
        ).returncode == 0)

        search = start("elasticsearch", "docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2",
                       options=("-e", "discovery.type=single-node", "-e",
                                "action.auto_create_index=false", "-e",
                                "ES_JAVA_OPTS=-Xms256m -Xmx256m"))
        wait_for("Elasticsearch ready", lambda: docker(
            "exec", search, "curl", "-fsS",
            "http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=2s",
            check=False
        ).returncode == 0)

        # Real issuer discovery runs against this local OIDC fixture. JWT security
        # stays enabled; no hosted Auth0 or Cloudinary service is contacted.
        oidc_script = '''
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
# Synthetic RSA public key for discovery/algorithm selection. Its private key
# was discarded; valid-token behavior is tested in integration/E2E tests.
modulus = "zDLMq8wxqOpXzCvfGXS7HbzNubtxCyuhDlqOgGoY0EUgg3k7s2IWBk_U90RxFz_jehk8BTIqFWinheUo7Mp4iAi4O6jUJsHw42cG2o_0yl8J7pGnr8B4aByk74VzAWdYtTLEJBfANDgawBjz2GZGVdKxfTovH17E6qqHwRTOOroTQbARAc2zK5ewrQjYtCrtPBo9ArrYMWyXJN84SQhPwkdSukw7jn-razNA0ZE5ndlF-6WQPScvPc0k-fXUtUGM_8Tt67TzM8ycQ4EFdrwJcRGifoweoOryFOiSShHOd5ohNRDRRpRN2RKLm8bTIcReSeLhj74QODTweig6828Ktw"
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/jwks":
            body = {"keys": [{"kty": "RSA", "kid": "smoke", "use": "sig",
                              "alg": "RS256", "n": modulus, "e": "AQAB"}]}
        else:
            body = {"issuer": "http://oidc:8000/", "jwks_uri": "http://oidc:8000/jwks",
                    "id_token_signing_alg_values_supported": ["RS256"]}
        payload = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(payload)
HTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
'''
        oidc = start("oidc", "python:3.13-alpine", "python", "-u", "-c", oidc_script)
        wait_for("OIDC fixture ready", lambda: docker(
            "exec", oidc, "python", "-c",
            "import urllib.request; urllib.request.urlopen('http://localhost:8000/.well-known/openid-configuration')",
            check=False
        ).returncode == 0)

        environment = {
            "SPRING_PROFILES_ACTIVE": "production",
            "MONGODB_URI": "mongodb://mongodb:27017/moodly_smoke_test?replicaSet=rs0",
            "ELASTICSEARCH_URIS": "http://elasticsearch:9200",
            "AUTH0_ISSUER_URI": "http://oidc:8000/",
            "AUTH0_AUDIENCE": "moodly-smoke",
            "CLOUDINARY_CLOUD_NAME": "smoke-fixture",
            "CLOUDINARY_API_KEY": "smoke-fixture",
            "CLOUDINARY_API_SECRET": "smoke-fixture",
            "CLOUDINARY_UPLOAD_PRESET": "smoke-fixture",
            "CLOUDINARY_FOLDER": "moodly/smoke",
            "APP_CORS_ALLOWED_ORIGINS": "http://localhost:3000",
            "MOODLY_ENTRIES_COLLECTION_NAME": "daily_entries",
            "MOODLY_CDC_STREAM_ID": "daily_entries",
            "MOODLY_CDC_ENABLED": "true",
            "MOODLY_CDC_RETRY_MAX_ATTEMPTS": "3",
            "MOODLY_CDC_RETRY_INITIAL_BACKOFF_MS": "200",
            "MOODLY_CDC_REINDEX_BATCH_SIZE": "250",
            "MOODLY_SEARCH_INDEX_NAME": "daily_entries_search",
            "MOODLY_SEARCH_INDEX_MAPPING_RESOURCE": "classpath:elasticsearch/daily_entries_search.json",
            "MOODLY_SEARCH_INDEX_INITIALIZATION_ENABLED": "true",
            "MOODLY_RATE_LIMIT_ENABLED": "true",
            "MOODLY_E2E_RESET_ENABLED": "false",
        }
        options = ["--memory", "768m"]
        for key, value in environment.items():
            options.extend(("-e", f"{key}={value}"))
        app = start("backend", args.image, options=options)

        def healthy():
            state = json.loads(docker("inspect", app).stdout)[0]["State"]
            if not state["Running"]:
                raise RuntimeError("Backend exited before becoming healthy")
            return state.get("Health", {}).get("Status") == "healthy"

        wait_for("backend Docker HEALTHCHECK", healthy, seconds=240)
        checks = '''
import json, urllib.request, urllib.error
def request(path, expected, method="GET"):
    try:
        response = urllib.request.urlopen(urllib.request.Request("http://backend:8080" + path, method=method))
        code, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, body = error.code, error.read()
    assert code in expected, (path, code, body)
    return body
health = json.loads(request("/actuator/health", (200,)))
assert health["status"] == "UP", health
assert "components" not in health and "details" not in health, health
request("/habits", (401,))
# Depending on the error dispatch security chain, an absent public reset route
# returns 404 or 401. An enabled reset controller would return 403 without token.
request("/internal/e2e/reset", (401, 404), method="POST")
request("/v3/api-docs", (401, 404))
print("PASS: health UP, production health privacy, protected API, reset and docs unavailable")
'''
        print(docker("exec", oidc, "python", "-c", checks).stdout.strip(), flush=True)
        docker("exec", search, "curl", "-fsS", "http://localhost:9200/daily_entries_search/_mapping")
        print("PASS: CDC initialized search mapping", flush=True)
        user = json.loads(docker("inspect", app).stdout)[0]["Config"]["User"]
        if user in ("", "root", "0", "0:0"):
            raise RuntimeError("Backend must run as a non-root user")
        print("PASS: backend runs as a non-root user", flush=True)
    except Exception:
        for name in containers:
            result = docker("logs", "--tail", "80", name, check=False)
            print(f"--- {name} ---\n{result.stdout}\n{result.stderr}", flush=True)
        raise
    finally:
        # Names are generated here; never remove existing local/test containers
        # or volumes. MongoDB fixture data lives in container-only tmpfs mounts.
        for name in reversed(containers):
            docker("rm", "-f", name, check=False)
        docker("network", "rm", prefix, check=False)


if __name__ == "__main__":
    main()
