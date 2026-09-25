import { reactive, watch } from 'vue';
import { useAuth } from './useAuth';

const DEFAULT = '#0c6b5c';
const state = reactive({
  appearance: localStorage.getItem('cms-appearance') || 'system',
  density: localStorage.getItem('cms-density') || 'comfortable',
});

let started = false;

function hexToRgb(hex) {
  const value = String(hex || '').replace('#', '');
  if (![3, 6].includes(value.length) || !/^[0-9a-f]+$/i.test(value)) return null;
  const full = value.length === 3 ? value.split('').map((c) => c + c).join('') : value;
  return {
    r: parseInt(full.slice(0, 2), 16),
    g: parseInt(full.slice(2, 4), 16),
    b: parseInt(full.slice(4, 6), 16),
  };
}

function applyAccent(hex) {
  const rgb = hexToRgb(hex || DEFAULT);
  const color = rgb ? `#${[rgb.r, rgb.g, rgb.b].map((n) => n.toString(16).padStart(2, '0')).join('')}` : DEFAULT;
  const root = document.documentElement;
  root.style.setProperty('--accent', color);
  root.style.setProperty('--color-accent', color);
  root.style.setProperty('--accent-hover', `color-mix(in srgb, ${color} 82%, #000)`);
  const mixInto = root.classList.contains('dark') ? '#1e1a16' : '#fffdf8';
  root.style.setProperty('--accent-soft', `color-mix(in srgb, ${color} 18%, ${mixInto})`);
}

function applyChrome() {
  const root = document.documentElement;
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = state.appearance === 'dark' || (state.appearance === 'system' && prefersDark);
  root.classList.toggle('dark', dark);
  root.classList.toggle('compact', state.density === 'compact');
  localStorage.setItem('cms-appearance', state.appearance);
  localStorage.setItem('cms-density', state.density);
}

function start() {
  if (started) return;
  started = true;
  const auth = useAuth();
  const refresh = () => {
    applyChrome();
    applyAccent(auth.state.organization?.primary_color || DEFAULT);
  };
  watch(() => [state.appearance, state.density, auth.state.organization?.primary_color], refresh, { immediate: true });
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', refresh);
}

export function useTheme() {
  start();
  return {
    state,
    setAppearance(value) {
      state.appearance = value;
    },
    toggleAppearance() {
      state.appearance = document.documentElement.classList.contains('dark') ? 'light' : 'dark';
    },
    setDensity(value) {
      state.density = value;
    },
  };
}
