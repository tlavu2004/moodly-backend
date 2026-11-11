# Backend Ctrl+C Exit Handling

## Behavior

A normal Ctrl+C is SIGINT, conventionally represented by exit status 130. The
backend may shut down correctly while GNU Make still reports a failed recipe if
its child script forwards that status unchanged.

The local/manual wrapper now traps SIGINT, keeps control until Java exits, and
reads a short cleanup-result marker under target/. Java records running before
resource acquisition and clean only after backend, data, infrastructure-session,
and test-lease cleanup succeeds. Cleanup failures record failed. The shell removes
the marker on exit; it contains no environment values or credentials.

Only a child status 130 accompanied by a received SIGINT and a clean/not-started
marker is normalized to a successful recipe return. An ordinary exit 130 without
SIGINT, an interrupted cleanup failure, and other failures remain errors. Other
tooling modes retain their existing exit behavior.

GNU Make itself can still return 130 when it receives the terminal's Ctrl+C. That
is the conventional interrupted-command status, not proof of cleanup failure.
The expected improvement is absence of the misleading `make: *** ... Error 130`
recipe-error line after successful cleanup; it is not a promise that every outer
terminal process returns zero after SIGINT.

## Windows/Git Bash Validation

An isolated fixture reused the actual pending Java BackendRun shutdown hook and
shell wrapper, with a fake Maven bootstrap. It acquired no Docker/backend resources
and used no real credentials. Console Ctrl+C was delivered through the Windows PTY.
A separate Windows process observer measured GNU Make's actual exit code.

| Case | Observed wrapper state | Outcome |
| --- | --- | --- |
| Ctrl+C, Java cleanup succeeds | interrupted=true, child=130, marker=clean | No make recipe Error 130; GNU Make itself returned SIGINT 130 |
| Child exits 130 without Ctrl+C | interrupted=false, child=130 | Error 130 retained; Make returned failure 2 |
| Ctrl+C with synthetic cleanup failure | interrupted=true, child=130, marker=failed | Error 130 retained; failure not hidden |

Full Maven verify remains the application regression check. The fixture and its
output live only under ignored target/; they are not production dependencies.

## Running Tests While Local Backend Is Active

Use make test or make verify, not make local-test (which is not a defined target).
A local backend and automated tests share infrastructure but have separate data
namespaces. The test session release does not stop services while the backend
session remains active. A manual test backend, however, exclusively owns the same
disposable namespace as automated tests, so concurrent automated runs are rejected.
