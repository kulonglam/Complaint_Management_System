import { watch } from 'vue';
import { useAuth } from './useAuth';

const DEFAULT = '#0c6b5c';

function hexToRgb(hex) {
  const value = hex.replace('#', '');
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
  root.style.setProperty('--accent-soft', `color-mix(in srgb, ${color} 16%, #fffdf8)`);
}

export function useTheme() {
  const auth = useAuth();
  watch(
    () => auth.state.organization?.primary_color,
    (color) => applyAccent(color || DEFAULT),
    { immediate: true }
  );
  return { applyAccent };
}
