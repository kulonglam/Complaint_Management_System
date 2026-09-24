import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const backendDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'backend');
const isWindows = process.platform === 'win32';
const command = isWindows ? 'mvnw.cmd' : './mvnw';

const child = spawn(command, ['spring-boot:run'], {
  cwd: backendDir,
  stdio: 'inherit',
  shell: isWindows,
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
