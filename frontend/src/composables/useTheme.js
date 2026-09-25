import { computed, effectScope, onScopeDispose, reactive, watch } from 'vue';
import { useAuth } from './useAuth';

const DEFAULT = '#0c6b5c';
const state = reactive({
  appearance: localStorage.getItem('cms-appearance') || 'system',
  density: localStorage.getItem('cms-density') || 'comfortable',
  prefersDark: false,
});

const isDark = computed(() => state.appearance === 'dark' || (state.appearance === 'system' && state.prefersDark));

let consumers = 0;
let scope = null;
let mediaQuery = null;
let onPrefersChange = null;

function getMedia() {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return { matches: false, addEventListener() {}, removeEventListener() {} };
  }
  return window.matchMedia('(prefers-color-scheme: dark)');
}

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
  const mixInto = isDark.value ? '#1e1a16' : '#fffdf8';
  root.style.setProperty('--accent-soft', `color-mix(in srgb, ${color} 18%, ${mixInto})`);
}

function applyChrome() {
  const root = document.documentElement;
  root.classList.toggle('dark', isDark.value);
  root.classList.toggle('compact', state.density === 'compact');
  localStorage.setItem('cms-appearance', state.appearance);
  localStorage.setItem('cms-density', state.density);
}

function ensureStarted(auth) {
  consumers += 1;
  if (scope) return;

  mediaQuery = getMedia();
  state.prefersDark = Boolean(mediaQuery.matches);
  onPrefersChange = (event) => {
    state.prefersDark = event.matches;
  };
  mediaQuery.addEventListener('change', onPrefersChange);

  scope = effectScope(true);
  scope.run(() => {
    watch(
      () => [state.appearance, state.density, state.prefersDark, auth.state.organization?.primary_color],
      () => {
        applyChrome();
        applyAccent(auth.state.organization?.primary_color || DEFAULT);
      },
      { immediate: true }
    );
  });
}

function release() {
  consumers = Math.max(0, consumers - 1);
  if (consumers > 0 || !scope) return;
  mediaQuery?.removeEventListener('change', onPrefersChange);
  scope.stop();
  scope = null;
  mediaQuery = null;
  onPrefersChange = null;
}

export function useTheme() {
  const auth = useAuth();
  ensureStarted(auth);
  onScopeDispose(release);

  return {
    state,
    isDark,
    setAppearance(value) {
      state.appearance = value;
    },
    toggleAppearance() {
      state.appearance = isDark.value ? 'light' : 'dark';
    },
    setDensity(value) {
      state.density = value;
    },
  };
}
