import { computed, reactive } from 'vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';

let store;
let channel;

export function useNotifications() {
  if (store) return store;

  const auth = useAuth();
  const state = reactive({ items: [] });
  const unreadCount = computed(() => state.items.filter((item) => !item.read_at).length);

  async function load() {
    if (!auth.state.profile?.id) {
      state.items = [];
      return;
    }
    const { data } = await supabase
      .from('notifications')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(50);
    state.items = data || [];
  }

  async function markRead(id) {
    await supabase.from('notifications').update({ read_at: new Date().toISOString() }).eq('id', id);
    state.items = state.items.map((item) => (item.id === id ? { ...item, read_at: new Date().toISOString() } : item));
  }

  async function markAll() {
    await supabase.from('notifications').update({ read_at: new Date().toISOString() }).is('read_at', null);
    state.items = state.items.map((item) => ({ ...item, read_at: item.read_at || new Date().toISOString() }));
  }

  function subscribe() {
    if (channel || !auth.state.profile?.id) return;
    channel = supabase
      .channel('notifications-feed')
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'notifications', filter: `user_id=eq.${auth.state.profile.id}` },
        (payload) => {
          state.items = [payload.new, ...state.items].slice(0, 50);
        }
      )
      .subscribe();
  }

  load().then(subscribe);

  store = {
    items: computed(() => state.items),
    unreadCount,
    load,
    markRead,
    markAll,
  };
  return store;
}
