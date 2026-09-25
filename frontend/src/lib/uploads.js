import { ALLOWED_FILE_TYPES, MAX_FILE_SIZE } from './constants';

const BLOCKED_EXTENSIONS = /\.(exe|bat|cmd|msi|dll|js|mjs|html|htm|svg|php|sh|ps1)$/i;

export function assertSafeUpload(file) {
  if (!file) throw new Error('Choose a file.');
  if (BLOCKED_EXTENSIONS.test(file.name)) {
    throw new Error(`${file.name} is a blocked file type.`);
  }
  if (file.type && !ALLOWED_FILE_TYPES.includes(file.type)) {
    throw new Error(`${file.name} is not an allowed file type.`);
  }
  if (file.size > MAX_FILE_SIZE) {
    throw new Error(`${file.name} must be 10MB or smaller.`);
  }
  return true;
}
