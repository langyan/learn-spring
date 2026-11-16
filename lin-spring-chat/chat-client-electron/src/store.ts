import { create } from 'zustand';
import type {
  ConversationView,
  MessageDto,
  PresenceEvent,
  ReadReceiptEvent,
  UserView,
} from './types';

export interface ChatState {
  me: UserView | null;
  conversations: ConversationView[];
  activeConversationId: string | null;
  /** conversationId -> 消息列表（升序） */
  messages: Record<string, MessageDto[]>;
  /** conversationId -> 已读到该毫秒位置的成员数（群）/对方是否已读（单聊由 receipts 推导） */
  receipts: Record<string, ReadReceiptEvent[]>;
  /** userId -> online */
  presence: Record<string, boolean>;
  connected: boolean;

  setMe: (me: UserView | null) => void;
  setConnected: (v: boolean) => void;
  setConversations: (list: ConversationView[]) => void;
  upsertConversation: (c: ConversationView) => void;
  removeConversation: (id: string) => void;
  setActive: (id: string | null) => void;
  appendMessage: (msg: MessageDto) => void;
  prependMessages: (conversationId: string, list: MessageDto[]) => void;
  markRecalled: (messageId: string) => void;
  applyReceipt: (e: ReadReceiptEvent) => void;
  applyPresence: (e: PresenceEvent) => void;
  clearUnread: (conversationId: string) => void;
  reset: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  me: null,
  conversations: [],
  activeConversationId: null,
  messages: {},
  receipts: {},
  presence: {},
  connected: false,

  setMe: (me) => set({ me }),
  setConnected: (v) => set({ connected: v }),

  setConversations: (list) => set({ conversations: list }),

  upsertConversation: (c) =>
    set((s) => {
      const exists = s.conversations.some((x) => x.id === c.id);
      return {
        conversations: exists
          ? s.conversations.map((x) => (x.id === c.id ? { ...x, ...c } : x))
          : [c, ...s.conversations],
      };
    }),

  removeConversation: (id) =>
    set((s) => ({
      conversations: s.conversations.filter((c) => c.id !== id),
      activeConversationId: s.activeConversationId === id ? null : s.activeConversationId,
    })),

  setActive: (id) => set({ activeConversationId: id }),

  appendMessage: (msg) =>
    set((s) => ({
      messages: {
        ...s.messages,
        [msg.conversationId]: [...(s.messages[msg.conversationId] ?? []), msg],
      },
      // 会话列表预览与时间同步更新
      conversations: s.conversations.map((c) =>
        c.id === msg.conversationId
          ? {
              ...c,
              lastMessagePreview:
                msg.type === 'SYSTEM' ? msg.content : msg.recalled ? '[消息已撤回]' : msg.content,
              lastMessageAtMillis: msg.createdAtMillis,
            }
          : c,
      ),
    })),

  prependMessages: (conversationId, list) =>
    set((s) => ({
      messages: {
        ...s.messages,
        [conversationId]: [...list, ...(s.messages[conversationId] ?? [])],
      },
    })),

  markRecalled: (messageId) =>
    set((s) => {
      const messages = { ...s.messages };
      for (const convId of Object.keys(messages)) {
        messages[convId] = messages[convId].map((m) =>
          m.id === messageId ? { ...m, recalled: true } : m,
        );
      }
      return { messages };
    }),

  applyReceipt: (e) =>
    set((s) => ({
      receipts: {
        ...s.receipts,
        [e.conversationId]: [...(s.receipts[e.conversationId] ?? []).filter((x) => x.userId !== e.userId), e],
      },
    })),

  applyPresence: (e) =>
    set((s) => ({
      presence: { ...s.presence, [e.userId]: e.online },
      conversations: s.conversations.map((c) =>
        c.peerId === e.userId ? { ...c, peerOnline: e.online } : c,
      ),
    })),

  clearUnread: (conversationId) =>
    set((s) => ({
      conversations: s.conversations.map((c) =>
        c.id === conversationId ? { ...c, unreadCount: 0 } : c,
      ),
    })),

  reset: () =>
    set({
      me: null,
      conversations: [],
      activeConversationId: null,
      messages: {},
      receipts: {},
      presence: {},
      connected: false,
    }),
}));
