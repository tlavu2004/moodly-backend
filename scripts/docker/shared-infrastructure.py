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


def compose(*args, env_files=None):
    command = ["docker", "compose", "-p", "moodly-local"]
    for path in env_files or [infrastructure_file()]:
        command.extend(("--env-file", str(path)))
    command.extend(("-f", str(ROOT / "compose.local.yaml"), *args))
    result = subprocess.run(command, cwd=ROOT, env=compose_environment(),
                            capture_output=True, text=True, timeout=180)
    if result.returncode:
        # Compose diagnostics can include expanded values. Keep them out of logs.
        raise RuntimeError("Shared Compose configuration failed; check the selected env file and Docker")
    return result.stdout


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
    validate_resources(local_database, local_index)

    def port(service):
        ports = service["ports"]
        if len(ports) != 1 or ports[0].get("host_ip") != "127.0.0.1":
            raise ValueError("Shared infrastructure must publish one loopback port per service")
        return str(ports[0]["published"])

    return {"envFile": infrastructure_file(), "MONGODB_HOST": "localhost",
            "MONGODB_CONTAINER_PORT": mongo["environment"]["MONGODB_CONTAINER_PORT"],
            "MONGODB_PORT": port(mongo), "MONGODB_REPLICA_SET": mongo["environment"]["MONGODB_REPLICA_SET"],
            "MONGODB_DATABASE": TEST_DATABASE, "ELASTICSEARCH_HOST": "localhost",
            "ELASTICSEARCH_PORT": port(search), "MOODLY_SEARCH_INDEX_NAME": TEST_INDEX}


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
        files = [infrastructure_file()]
        if (ROOT / ".env.test").exists():
            files.append(ROOT / ".env.test")
        environment = os.environ.copy()
        # Manual mode intentionally uses hosted development credentials. Dotenv
        # parsing belongs to Compose; never source/eval credential assignments.
        for assignment in compose("config", "--environment", env_files=files).splitlines():
            key, separator, value = assignment.partition("=")
            if separator and key.startswith(("APP_", "AUTH0_", "CLOUDINARY_", "MOODLY_")):
                environment[key] = value
        environment.update({key: value for key, value in config.items() if key != "envFile"})
        environment["MOODLY_INFRA_ENV_FILE"] = config["envFile"]
        environment["SPRING_PROFILES_ACTIVE"] = "compose-test"
        environment["SERVER_PORT"] = environment.get("MOODLY_TEST_SERVER_PORT", "8081")
        environment["SPRING_MONGODB_URI"] = (f"mongodb://localhost:{config['MONGODB_PORT']}/"
                                               f"{TEST_DATABASE}?replicaSet={config['MONGODB_REPLICA_SET']}")
        environment["SPRING_ELASTICSEARCH_URIS"] = f"http://localhost:{config['ELASTICSEARCH_PORT']}"
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
    parser.add_argument("mode", choices=("config", "manual", "clean-test"))
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
                parser.error("manual mode requires a command")
            sys.exit(manual(options.command))
    except (RuntimeError, ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
