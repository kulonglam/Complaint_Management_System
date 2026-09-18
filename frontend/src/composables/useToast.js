import { computed, reactive } from 'vue';

let toast;

export function createToastState() {
  if (toast) return toast;
  const toasts = reactive([]);

  function push(message, tone = 'info') {
    const id = crypto.randomUUID();
    toasts.push({ id, message, tone });
    setTimeout(() => dismiss(id), 5000);
  }

  function dismiss(id) {
    const index = toasts.findIndex((item) => item.id === id);
    if (index >= 0) toasts.splice(index, 1);
  }

  toast = {
    toasts,
    success: (message) => push(message, 'success'),
    error: (message) => push(message, 'danger'),
    info: (message) => push(message, 'info'),
    dismiss,
  };
  return toast;
}

export function useToast() {
  return createToastState();
}
