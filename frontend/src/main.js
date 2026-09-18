import { createApp } from 'vue';
import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query';
import App from './App.vue';
import router from './router';
import { createAuthState } from './composables/useAuth';
import { createToastState } from './composables/useToast';
import './assets/styles.css';

createAuthState();
createToastState();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

const app = createApp(App);
app.use(VueQueryPlugin, { queryClient });
app.use(router);
app.mount('#app');
