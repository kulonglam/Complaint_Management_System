<template>
  <Line
    v-if="type === 'line'"
    :data="chartData"
    :options="options"
  />
  <Bar
    v-else
    :data="chartData"
    :options="options"
  />
</template>

<script setup>
import { computed } from 'vue';
import { Bar, Line } from 'vue-chartjs';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Tooltip,
  Legend,
} from 'chart.js';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, BarElement, Tooltip, Legend);

const props = defineProps({
  type: { type: String, default: 'bar' },
  labels: { type: Array, default: () => [] },
  values: { type: Array, default: () => [] },
  label: { type: String, default: 'Count' },
});

function theme() {
  const styles = getComputedStyle(document.documentElement);
  return {
    accent: styles.getPropertyValue('--accent').trim() || '#0c6b5c',
    muted: styles.getPropertyValue('--muted').trim() || '#6a645a',
    line: styles.getPropertyValue('--line').trim() || '#e4d8c8',
  };
}

const chartData = computed(() => {
  const colors = theme();
  return {
    labels: props.labels,
    datasets: [
      {
        label: props.label,
        data: props.values,
        backgroundColor: colors.accent,
        borderColor: colors.accent,
        borderRadius: 6,
        tension: 0.3,
      },
    ],
  };
});

const options = computed(() => {
  const colors = theme();
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: colors.muted }, grid: { color: colors.line } },
      y: { beginAtZero: true, ticks: { precision: 0, color: colors.muted }, grid: { color: colors.line } },
    },
  };
});
</script>
