import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, cpSync, rmSync, symlinkSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'rent development-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  cpSync('scripts/development', join(root, 'scripts/development'), { recursive: true })
  for (const dir of ['bin', 'apps/api', 'apps/recommendations', 'apps/web/node_modules/.bin']) mkdirSync(join(root, dir), { recursive: true })
  const log = join(root, 'calls')
  const stub = (name, body) => writeFileSync(join(root, name), '#!/bin/bash\n' + body, { mode: 0o755 })
  const record = 'echo "$0 $* HDFS=$HDFS_ARCHIVE_ENABLED API=$API_TARGET" >> "$CALLS"\n'
  for (const name of ['bin/npm', 'apps/api/mvnw', 'apps/recommendations/mvnw', 'apps/web/node_modules/.bin/concurrently']) stub(name, record)
  const run = (script, args = [], env = {}) => spawnSync('/bin/bash', [join(root, 'scripts/development', script), ...args], { env: { ...process.env, PATH: join(root, 'bin') + ':/usr/bin:/bin', CALLS: log, ...env }, encoding: 'utf8' })
  return { root, stub, run, calls: () => readFileSync(log, 'utf8') }
}

for (const app of ['api', 'web', 'recommendations']) test(`Given ${app}, when launched, then its command receives the shared development environment`, t => {
  const f = fixture(t)
  assert.equal(f.run('app.sh', [app], { HDFS_ENABLED: 'true', SERVER_PORT: '8181', API_TARGET: '' }).status, 0)
  assert.match(f.calls(), /HDFS=true API=http:\/\/127.0.0.1:8181/)
  assert.match(f.calls(), app === 'web' ? /--strictPort/ : /spring-boot:run/)
})
test('Given an unknown application, when launched, then usage fails without running a service', t => {
  const f = fixture(t)
  assert.equal(f.run('app.sh', ['unknown']).status, 2)
})
test('Given foreground mode, when started, then root coordinates all applications and announces startup rather than readiness', t => {
  const f = fixture(t)
  const result = f.run('start.sh')
  assert.equal(result.status, 0)
  assert.match(result.stdout, /after each application finishes startup/)
  assert.match(f.calls(), /--kill-others-on-fail/)
  for (const app of ['api', 'web', 'recommendations']) assert.match(f.calls(), new RegExp('app.sh ' + app))
})
for (const exists of [true, false]) for (const nested of [true, false]) test(`Given session exists=${exists} and nested=${nested}, when tmux starts, then preparation is conditional and attachment is correct`, t => {
  const f = fixture(t)
  f.stub('bin/make', 'echo "prepare $*" >> "$CALLS"')
  f.stub('bin/tmux', `echo "$*" >> "$CALLS"\nif [[ "$1" == has-session ]]; then exit ${exists ? 0 : 1}; fi`)
  assert.equal(f.run('tmux.sh', [], { TMUX: nested ? 'existing' : '' }).status, 0)
  const calls = f.calls()
  assert.equal(calls.includes('prepare dev-prepare'), !exists)
  assert.equal(calls.includes('new-session'), !exists)
  assert.match(calls, nested ? /switch-client -t rent-cars/ : /attach-session -t rent-cars/)
  if (!exists) for (const app of ['api', 'web', 'recommendations']) assert.match(calls, new RegExp('app.sh ' + app))
})
test('Given infrastructure preparation fails, when tmux starts, then no session is created', t => {
  const f = fixture(t)
  f.stub('bin/tmux', 'echo "$*" >> "$CALLS"; exit 1')
  f.stub('bin/make', 'exit 7')
  assert.equal(f.run('tmux.sh').status, 7)
  assert.doesNotMatch(f.calls(), /new-session/)
})
for (const healthy of [true, false]) test(`Given prerequisites healthy=${healthy}, when doctor runs, then it reports actionable status without starting services`, t => {
  const f = fixture(t)
  f.stub('bin/java', `echo 'openjdk version "${healthy ? '25.0.1' : '21.0.1'}"' >&2`)
  f.stub('bin/javac', `echo 'javac ${healthy ? '25.0.1' : '21'}'`)
  f.stub('bin/node', `echo '${healthy ? 'v24.16.0' : 'v20.0.0'}'`)
  f.stub('bin/docker', `echo "$*" >> "$CALLS"; exit ${healthy ? 0 : 1}`)
  for (const name of ['jq', 'make', 'curl']) f.stub('bin/' + name, 'exit 0')
  f.stub('bin/lsof', 'exit 1')
  const result = f.run('doctor.sh')
  assert.equal(result.status, healthy ? 0 : 1)
  assert.match(result.stdout, healthy ? /Prerequisites OK/ : /Select JDK 25/)
  assert.doesNotMatch(f.calls(), /compose up|compose stop/)
})

test('Given tmux is missing, when optional mode starts, then it suggests the default command', t => {
  const f = fixture(t)
  symlinkSync('/usr/bin/dirname', join(f.root, 'bin/dirname'))
  const result = f.run('tmux.sh', [], { PATH: join(f.root, 'bin') })
  assert.equal(result.status, 1)
  assert.match(result.stderr, /Install tmux, or use make dev/)
})
test('Given tools and dependencies are missing, when doctor runs, then failures are aggregated', t => {
  const f = fixture(t)
  symlinkSync('/usr/bin/dirname', join(f.root, 'bin/dirname'))
  rmSync(join(f.root, 'apps/web/node_modules/.bin/concurrently'))
  const result = f.run('doctor.sh', [], { PATH: join(f.root, 'bin') })
  assert.equal(result.status, 1)
  assert.match(result.stdout, /Install java/)
  assert.match(result.stdout, /Install docker/)
  assert.match(result.stdout, /Run make setup/)
  assert.match(result.stdout, /Install lsof/)
})
test('Given custom environment values, when creating tmux windows, then overrides are quoted and forwarded', t => {
  const f = fixture(t)
  f.stub('bin/make', 'exit 0')
  f.stub('bin/tmux', 'echo "$*" >> "$CALLS"; if [[ "$1" == has-session ]]; then exit 1; fi')
  assert.equal(f.run('tmux.sh', [], { HDFS_ENABLED: 'true', SERVER_PORT: '8181', API_TARGET: 'http://localhost:8181', TMUX: '' }).status, 0)
  assert.match(f.calls(), /HDFS_ENABLED=true/)
  assert.match(f.calls(), /SERVER_PORT=8181/)
  assert.match(f.calls(), /API_TARGET=http:\/\/localhost:8181/)
})
