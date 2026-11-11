"""Resolve shared infrastructure and lease reserved test data without logging secrets."""

import argparse
import contextlib
import json
import os
import re
from pathlib import Path
import signal
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
TEST_DATABASE = "moodly_test"
TEST_INDEX = "daily_entries_search_test"
LOCK_FILE = ROOT / ".moodly-test.lock"


def compose_environment():
    # Caller test settings must never reconfigure the retained infrastructure.
    return {key: value for key, value in os.environ.items()
            if not key.startswith(("MONGODB_", "ELASTICSEARCH_"))
            and key != "MOODLY_SEARCH_INDEX_NAME"}


def infrastructure_file():
    selected = os.environ.get("MOODLY_INFRA_ENV_FILE")
    path = Path(selected) if selected else ROOT / (".env.local" if (ROOT / ".env.local").exists() else ".env.local.example")
    return str(path.resolve())


def compose(*args, env_files=None, definition=None):
    command = ["docker", "compose", "-p", "moodly-local"]
    for path in env_files or [infrastructure_file()]:
        command.extend(("--env-file", str(path)))
    command.extend(("-f", "-" if definition is not None else str(ROOT / "compose.local.yaml"), *args))
    result = subprocess.run(command, cwd=ROOT, env=compose_environment(),
                            input=json.dumps(definition) if definition is not None else None,
                            capture_output=True, text=True, timeout=180)
    if result.returncode:
        # Compose diagnostics can include expanded values. Keep them out of logs.
        raise RuntimeError("Shared Compose configuration failed; check the selected env file and Docker")
    return result.stdout


def test_file():
    return str(ROOT / (".env.test" if (ROOT / ".env.test").exists() else ".env.test.example"))


def dotenv_keys(path):
    keys = set()
    quote = None
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if quote is None:
            match = re.match(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*[=:]", line)
            if not match:
                continue
            keys.add(match.group(1))
            value = line[match.end():].lstrip()
            if not value or value[0] not in ("'", '"'):
                continue
            quote, line = value[0], value[1:]
        escaped = False
        for character in line:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
                break
    return keys


def runtime_environment(files):
    keys = set().union(*(dotenv_keys(path) for path in files))
    # Ask Compose for a JSON environment mapping so literal pipes, quotes and
    # multiline values survive intact. Nothing is evaluated by a shell.
    definition = {"services": {"runtime": {"image": "scratch", "environment":
                  {key: "${" + key + "}" for key in sorted(keys)}}}}
    resolved = json.loads(compose("config", "--format", "json", env_files=files, definition=definition))
    environment = os.environ.copy()
    # `config` renders a reusable Compose document and doubles literal dollars.
    # Decode that serialization once before passing values to a native process.
    environment.update({key: value.replace("$$", "$")
                        for key, value in resolved["services"]["runtime"]["environment"].items()})
    return environment


def validate_resources(local_database, local_index, database=TEST_DATABASE, index=TEST_INDEX):
    if database != TEST_DATABASE or index != TEST_INDEX:
        raise ValueError("Only the exact reserved test database/index may be reset")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", local_database or "") or not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", local_index or ""):
        raise ValueError("Local database/index must be explicit concrete names without wildcards or lists")
    if local_database == database or local_index == index:
        raise ValueError("Local resources collide with reserved test resources; refusing test startup")


def configuration():
    services = json.loads(compose("config", "--format", "json"))["services"]
    mongo, search = services["mongodb"], services["elasticsearch"]
    local_database = mongo["labels"]["io.moodly.local-database"]
    local_index = search["labels"]["io.moodly.local-index"]
    local_settings = runtime_environment([infrastructure_file()])
    mongo_host = local_settings.get("MONGODB_HOST")
    search_host = local_settings.get("ELASTICSEARCH_HOST")
    if mongo_host not in ("localhost", "127.0.0.1") or search_host not in ("localhost", "127.0.0.1"):
        raise ValueError("Shared local/test hosts must be loopback hosts from the infrastructure env file")
    test = runtime_environment([infrastructure_file(), test_file()])
    database = test.get("MONGODB_DATABASE", "")
    index = test.get("MOODLY_SEARCH_INDEX_NAME", "")
    validate_resources(local_database, local_index, database, index)

    def port(service):
        ports = service["ports"]
        if len(ports) != 1 or ports[0].get("host_ip") != "127.0.0.1":
            raise ValueError("Shared infrastructure must publish one loopback port per service")
        return str(ports[0]["published"])

    return {"envFile": infrastructure_file(), "MONGODB_HOST": mongo_host,
            "MONGODB_CONTAINER_PORT": mongo["environment"]["MONGODB_CONTAINER_PORT"],
            "MONGODB_PORT": port(mongo), "MONGODB_REPLICA_SET": mongo["environment"]["MONGODB_REPLICA_SET"],
            "MONGODB_DATABASE": database, "ELASTICSEARCH_HOST": search_host,
            "ELASTICSEARCH_PORT": port(search), "MOODLY_SEARCH_INDEX_NAME": index}


@contextlib.contextmanager
def test_lease():
    # Java FileLock and these OS locks overlap the same byte range. Keep the lock
    # outside target/: Maven clean must not unlink a live lease.
    with LOCK_FILE.open("a+b") as lease:
        lease.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(lease.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.lockf(lease.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise RuntimeError("Test resources are in use; stop the other manual/automated test session") from error
        try:
            yield
        finally:
            lease.seek(0)
            if os.name == "nt":
                msvcrt.locking(lease.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.lockf(lease.fileno(), fcntl.LOCK_UN)


def manual(command):
    with test_lease():
        config = configuration()
        environment = runtime_environment([infrastructure_file(), test_file()])
        environment.update({key: value for key, value in config.items() if key != "envFile"})
        environment["MOODLY_INFRA_ENV_FILE"] = config["envFile"]
        environment["SPRING_PROFILES_ACTIVE"] = "compose-test"
        if not environment.get("SERVER_PORT", "").isdigit():
            raise ValueError("SERVER_PORT must be set to an integer in the test env file")
        environment["SPRING_MONGODB_URI"] = (f"mongodb://{config['MONGODB_HOST']}:{config['MONGODB_PORT']}/"
                                               f"{TEST_DATABASE}?replicaSet={config['MONGODB_REPLICA_SET']}")
        environment["SPRING_ELASTICSEARCH_URIS"] = f"http://{config['ELASTICSEARCH_HOST']}:{config['ELASTICSEARCH_PORT']}"
        return run_child(command, environment)


def local(command):
    config = configuration()
    environment = runtime_environment([infrastructure_file()])
    for key, value in config.items():
        if key not in ("envFile", "MONGODB_DATABASE", "MOODLY_SEARCH_INDEX_NAME"):
            environment[key] = value
    environment["MOODLY_INFRA_ENV_FILE"] = config["envFile"]
    environment["SPRING_PROFILES_ACTIVE"] = "local"
    environment["SPRING_MONGODB_URI"] = (f"mongodb://{config['MONGODB_HOST']}:{config['MONGODB_PORT']}/"
                                          f"{environment['MONGODB_DATABASE']}?replicaSet={config['MONGODB_REPLICA_SET']}")
    environment["SPRING_ELASTICSEARCH_URIS"] = f"http://{config['ELASTICSEARCH_HOST']}:{config['ELASTICSEARCH_PORT']}"
    return run_child(command, environment)


def run_child(command, environment):
    port = environment.get("SERVER_PORT", "")
    if not port.isdigit() or not 1 <= int(port) <= 65535:
        raise ValueError("Set a valid SERVER_PORT in the selected runtime env file")
    child = subprocess.Popen(command, cwd=ROOT, env=environment,
                             start_new_session=os.name != "nt",
                             creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0)
    def stop_child(*_):
        if child.poll() is not None:
            return
        if os.name == "nt":
            # Terminate only the tree started above, including Maven's JVM;
            # releasing the lease while a writer survives would be unsafe.
            subprocess.run(["taskkill", "/PID", str(child.pid), "/T", "/F"],
                           capture_output=True, timeout=30)
        else:
            os.killpg(child.pid, signal.SIGTERM)
        try:
            child.wait(timeout=30)
        except subprocess.TimeoutExpired:
            if os.name != "nt":
                os.killpg(child.pid, signal.SIGKILL)
            else:
                child.kill()
            child.wait()
    signals = (signal.SIGTERM, signal.SIGBREAK) if hasattr(signal, "SIGBREAK") else (signal.SIGTERM,)
    previous = {number: signal.signal(number, stop_child) for number in signals}
    try:
        return child.wait()
    except KeyboardInterrupt:
        stop_child()
        return 130
    finally:
        stop_child()
        for number, handler in previous.items():
            signal.signal(number, handler)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("config", "manual", "local", "clean-test"))
    parser.add_argument("command", nargs=argparse.REMAINDER)
    options = parser.parse_args()
    try:
        if options.mode == "config":
            print(json.dumps(configuration()))
        elif options.mode == "clean-test":
            with test_lease():
                config = configuration()
                if input("Drop reserved MongoDB database moodly_test? Type yes: ") != "yes":
                    print("Cancelled; no data changed")
                else:
                    compose("exec", "-T", "mongodb", "mongosh", "--quiet", "--port", config["MONGODB_CONTAINER_PORT"],
                            "--eval", "if (!db.getSiblingDB('moodly_test').dropDatabase().ok) { quit(1); }")
                    print("Reserved test MongoDB database cleared; search projection may need reindexing")
        else:
            if not options.command:
                parser.error("runtime mode requires a command")
            sys.exit((manual if options.mode == "manual" else local)(options.command))
    except (RuntimeError, ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
