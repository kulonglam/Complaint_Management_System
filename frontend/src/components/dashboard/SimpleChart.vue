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

const chartData = computed(() => ({
  labels: props.labels,
  datasets: [
    {
      label: props.label,
      data: props.values,
      backgroundColor: '#2563eb',
      borderColor: '#2563eb',
      borderRadius: 6,
      tension: 0.3,
    },
  ],
}));

const options = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
};
</script>
