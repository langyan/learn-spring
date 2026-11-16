import { Client } from '@stomp/stompjs';
import { messageApi } from './api';
import { useChatStore } from './store';
import type { ConversationEvent, MessageDto, PresenceEvent, ReadReceiptEvent } from './types';

export const WS_URL = 'ws://localhost:8081/ws/chat';

let client: Client | null = null;
let heartbeatTimer: number | null = null;

export function connectStomp(token: string) {
  disconnectStomp();
  const store = useChatStore.getState();

  client = new Client({
    brokerURL: WS_URL,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,

    onConnect: () => {
      console.log('[stomp] connected');
      store.setConnected(true);

      // 新消息（含系统消息）
      client!.subscribe('/user/queue/messages', (frame) => {
        const msg = JSON.parse(frame.body) as MessageDto;
        const s = useChatStore.getState();
        s.appendMessage(msg);
        // 活跃会话自动已读上报
        if (s.activeConversationId === msg.conversationId && s.me) {
          messageApi.markRead(msg.conversationId, msg.id).catch(() => undefined);
        }
      });

      // 已读回执（双勾/群已读数）
      client!.subscribe('/user/queue/receipts', (frame) => {
        useChatStore.getState().applyReceipt(JSON.parse(frame.body) as ReadReceiptEvent);
      });

      // 在线状态变化
      client!.subscribe('/user/queue/presence', (frame) => {
        useChatStore.getState().applyPresence(JSON.parse(frame.body) as PresenceEvent);
      });

      // 会话变更（建群/邀请/移除/退群/解散/改名/撤回）
      client!.subscribe('/user/queue/conversations', (frame) => {
        const event = JSON.parse(frame.body) as ConversationEvent;
        console.log('[conversation-event]', event.action, event.conversationId);
        if (event.action === 'MESSAGE_RECALLED' && event.refId) {
          useChatStore.getState().markRecalled(event.refId);
          return;
        }
        if (event.action === 'DISSOLVED') {
          useChatStore.getState().removeConversation(event.conversationId);
          return;
        }
        // 其余变更：拉取最新会话列表与（若在当前会话）详情
        void refreshConversations();
      });

      // 上线：拉取离线补投 + 会话列表
      void bootstrapAfterOnline();
      startHeartbeat();
    },

    onWebSocketClose: () => {
      useChatStore.getState().setConnected(false);
    },
    onStompError: (frame) => {
      console.error('[stomp] error', frame.headers['message']);
    },
  });

  client.activate();
}

export function disconnectStomp() {
  stopHeartbeat();
  if (client) {
    client.deactivate();
    client = null;
  }
}

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = window.setInterval(() => {
    if (client?.connected) {
      client.publish({ destination: '/app/heartbeat', body: '' });
    }
  }, 20000);
}

function stopHeartbeat() {
  if (heartbeatTimer !== null) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

async function bootstrapAfterOnline() {
  const s = useChatStore.getState();
  try {
    const undelivered = await messageApi.undelivered();
    undelivered.forEach((m) => s.appendMessage(m));
  } catch (e) {
    console.warn('补投拉取失败', e);
  }
  await refreshConversations();
}

export async function refreshConversations() {
  const { conversationApi } = await import('./api');
  try {
    const list = await conversationApi.list();
    useChatStore.getState().setConversations(list);
  } catch (e) {
    console.warn('会话列表刷新失败', e);
  }
}
