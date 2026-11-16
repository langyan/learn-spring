import { useEffect, useRef, useState } from 'react';
import { conversationApi } from '../api';
import { refreshConversations } from '../stomp';
import { useChatStore } from '../store';
import ChatWindow from './ChatWindow';
import ChatDetailPanel from './ChatDetailPanel';
import NewChatModal from './NewChatModal';

export default function MainLayout({ onLogout }: { onLogout: () => void }) {
  const me = useChatStore((s) => s.me);
  const connected = useChatStore((s) => s.connected);
  const conversations = useChatStore((s) => s.conversations);
  const presence = useChatStore((s) => s.presence);
  const activeConversationId = useChatStore((s) => s.activeConversationId);
  const setActive = useChatStore((s) => s.setActive);

  const [showNewChat, setShowNewChat] = useState(false);
  const [showDetail, setShowDetail] = useState(true);
  const [search, setSearch] = useState('');
  const bootstrapped = useRef(false);

  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;
    void refreshConversations();
  }, []);

  const active = conversations.find((c) => c.id === activeConversationId) ?? null;

  const filtered = conversations.filter((c) =>
    c.name?.toLowerCase().includes(search.trim().toLowerCase()),
  );

  return (
    <div className="app-bg flex h-full">
      {/* 侧栏：会话列表 */}
      <aside className="glass flex w-[300px] shrink-0 flex-col border-r border-white/8">
        <div className="flex items-center gap-3 px-4 py-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-purple-500 text-sm font-bold text-white">
            {me?.nickname?.[0]?.toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-medium">{me?.nickname}</div>
            <div className="flex items-center gap-1 text-[11px] text-white/40">
              <span
                className={`inline-block h-1.5 w-1.5 rounded-full ${connected ? 'bg-green-400' : 'bg-red-400'}`}
              />
              {connected ? '已连接' : '连接中…'}
            </div>
          </div>
          <button
            className="rounded-lg p-1.5 text-white/40 transition hover:bg-white/10 hover:text-white/70"
            title="发起新聊天"
            onClick={() => setShowNewChat(true)}
          >
            ➕
          </button>
          <button
            className="rounded-lg p-1.5 text-white/40 transition hover:bg-white/10 hover:text-white/70"
            title="退出登录"
            onClick={onLogout}
          >
            ⏏
          </button>
        </div>

        <div className="px-3 pb-2">
          <input
            className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs outline-none transition focus:border-indigo-400/50"
            placeholder="搜索会话…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="flex-1 space-y-1 overflow-y-auto px-2 pb-3">
          {filtered.length === 0 && (
            <div className="px-3 py-8 text-center text-xs text-white/30">
              暂无会话，点击 ➕ 发起聊天
            </div>
          )}
          {filtered.map((c) => {
            const online = c.peerId ? (presence[c.peerId] ?? c.peerOnline) : false;
            const isActive = c.id === activeConversationId;
            return (
              <button
                key={c.id}
                onClick={() => setActive(c.id)}
                className={`msg-in flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition ${
                  isActive ? 'bg-white/10' : 'hover:bg-white/5'
                }`}
              >
                <div
                  className={`presence-dot flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-sm font-bold text-white ${
                    c.type === 'GROUP'
                      ? 'bg-gradient-to-br from-teal-500 to-cyan-600'
                      : 'bg-gradient-to-br from-indigo-500 to-purple-600'
                  } ${online ? 'online' : ''}`}
                >
                  <span className="absolute bottom-0 right-0 hidden" />
                  {c.type === 'GROUP' ? '👥' : c.name?.[0]?.toUpperCase()}
                  {c.type === 'DIRECT' && online && (
                    <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-[#111319] bg-green-400" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-medium">{c.name || '未命名'}</span>
                    <span className="shrink-0 text-[10px] text-white/30">
                      {c.lastMessageAtMillis ? formatTime(c.lastMessageAtMillis) : ''}
                    </span>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-xs text-white/40">
                      {c.lastMessagePreview ?? '暂无消息'}
                    </span>
                    {c.unreadCount > 0 && (
                      <span className="msg-in shrink-0 rounded-full bg-gradient-to-r from-indigo-500 to-purple-500 px-1.5 py-0.5 text-[10px] font-bold text-white">
                        {c.unreadCount > 99 ? '99+' : c.unreadCount}
                      </span>
                    )}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </aside>

      {/* 中间：聊天区 */}
      <main className="flex min-w-0 flex-1 flex-col">
        {active ? (
          <ChatWindow
            key={active.id}
            conversation={active}
            onToggleDetail={() => setShowDetail((v) => !v)}
          />
        ) : (
          <div className="flex flex-1 flex-col items-center justify-center text-white/25">
            <div className="mb-3 text-5xl">💬</div>
            <div className="text-sm">选择一个会话开始聊天</div>
          </div>
        )}
      </main>

      {/* 右侧：详情面板（群成员/对方资料） */}
      {active && showDetail && (
        <ChatDetailPanel
          conversation={active}
          onRefresh={() => {
            void refreshConversations();
            void conversationApi.detail(active.id).then((d) => useChatStore.getState().upsertConversation(d)).catch(() => undefined);
          }}
          onClose={() => setShowDetail(false)}
        />
      )}

      {showNewChat && (
        <NewChatModal
          onClose={() => setShowNewChat(false)}
          onCreated={(conv) => {
            useChatStore.getState().upsertConversation(conv);
            useChatStore.getState().setActive(conv.id);
            setShowNewChat(false);
          }}
        />
      )}
    </div>
  );
}

function formatTime(millis: number): string {
  const d = new Date(millis);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return `${d.getMonth() + 1}/${d.getDate()}`;
}
