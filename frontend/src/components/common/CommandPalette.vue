<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[70] flex items-start justify-center bg-[#161310]/45 p-4 pt-[12vh] backdrop-blur-[2px]"
      @click.self="close"
    >
      <div role="dialog" aria-modal="true" aria-label="Command palette" class="surface w-full max-w-lg overflow-hidden rounded-3xl">
        <input
          ref="input"
          v-model="query"
          class="field-input rounded-none border-0 border-b border-[var(--line)]"
          placeholder="Go to a page or search cases…"
          @keydown.esc="close"
          @keydown.enter="run(filtered[0])"
        />
        <ul class="max-h-80 overflow-y-auto p-2">
          <li v-for="item in filtered" :key="String(item.id)">
            <button
              class="flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-sm hover:bg-[var(--paper)]"
              type="button"
              @click="run(item)"
            >
              <span>{{ item.label }}</span>
              <span class="text-xs text-muted">{{ item.hint }}</span>
            </button>
          </li>
          <li v-if="!filtered.length" class="px-3 py-6 text-center text-sm text-muted">No matching command.</li>
        </ul>
        <p class="border-t border-[var(--line)] px-4 py-2 text-xs text-muted">Esc to close · Enter to go</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

const props = defineProps({
  commands: { type: Array, default: () => [] },
});

const router = useRouter();
const open = ref(false);
const query = ref('');
const input = ref(null);

const filtered = computed(() => {
  const term = query.value.trim().toLowerCase();
  const list = props.commands;
  if (!term) return list.slice(0, 10);
  return list.filter((item) => item.label.toLowerCase().includes(term) || item.hint?.toLowerCase().includes(term)).slice(0, 12);
});

function close() {
  open.value = false;
  query.value = '';
}

function run(item) {
  if (!item) {
    if (query.value.trim()) router.push({ path: '/complaints', query: { q: query.value.trim() } });
    close();
    return;
  }
  if (item.to) router.push(item.to);
  if (item.run) item.run();
  close();
}

function onKey(event) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    open.value = !open.value;
  }
}

watch(open, async (value) => {
  if (value) {
    await nextTick();
    input.value?.focus();
  }
});

function openPalette() {
  open.value = true;
}

onMounted(() => {
  window.addEventListener('keydown', onKey);
  window.addEventListener('cms-palette', openPalette);
});
onUnmounted(() => {
  window.removeEventListener('keydown', onKey);
  window.removeEventListener('cms-palette', openPalette);
});
</script>
