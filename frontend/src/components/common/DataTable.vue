<template>
  <div class="surface overflow-hidden rounded-2xl">
    <div class="hidden overflow-x-auto md:block">
      <table class="min-w-full text-sm">
        <thead class="bg-[var(--paper)] text-left text-xs uppercase tracking-wide text-muted">
          <tr>
            <th v-for="column in columns" :key="column.key" class="px-4 py-3">
              <button
                v-if="column.sortable"
                class="inline-flex items-center gap-1 font-semibold uppercase"
                type="button"
                @click="$emit('sort', column.key)"
              >
                {{ column.label }}
                <span v-if="sort === column.key" aria-hidden="true">{{ ascending ? '↑' : '↓' }}</span>
              </button>
              <span v-else>{{ column.label }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, index) in rows"
            :key="row.id || index"
            class="border-t"
            :class="clickable ? 'cursor-pointer hover:bg-[var(--paper)]' : ''"
            @click="clickable && $emit('row-click', row)"
          >
            <td v-for="column in columns" :key="column.key" class="px-4 py-3">
              <slot :name="column.key" :row="row">{{ display(row, column.key) }}</slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="space-y-3 p-4 md:hidden">
      <article
        v-for="(row, index) in rows"
        :key="row.id || index"
        class="rounded-xl border border-[var(--line)] bg-[var(--paper)] p-4"
        @click="clickable && $emit('row-click', row)"
      >
        <slot name="card" :row="row">
          <p class="font-semibold">{{ row.title || row.name || row.reference_number }}</p>
        </slot>
      </article>
    </div>
    <div v-if="total != null" class="flex items-center justify-between border-t px-4 py-3 text-sm">
      <p>{{ total }} records</p>
      <div class="flex gap-2">
        <slot name="pagination" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  sort: String,
  ascending: Boolean,
  clickable: Boolean,
  total: Number,
});

defineEmits(['sort', 'row-click']);

function display(row, key) {
  return key.split('.').reduce((value, part) => value?.[part], row) ?? '—';
}
</script>
