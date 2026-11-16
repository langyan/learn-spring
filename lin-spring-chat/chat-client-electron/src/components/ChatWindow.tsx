import { useEffect, useRef, useState } from 'react';
import { messageApi } from '../api';
import { useChatStore } from '../store';
import type { ConversationView, MessageDto } from '../types';

export default function ChatWindow({
  conversation,
  onToggleDetail,
}: {
  conversation: ConversationView;
  onToggleDetail: () => void;
}) {
  const me = useChatStore((s) => s.me);
  const messages = useChatStore((s) => s.messages[conversation.id] ?? []);
  const receipts = useChatStore((s) => s.receipts[conversation.id] ?? []);
  const setActive = useChatStore((s) => s.setActive);

  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const atTopRef = useRef(false);
  const loadedRef = useRef(false);

  // 首次进入：加载最近消息 + 上报已读
  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;
    void (async () => {
      try {
        const list = await messageApi.history(conversation.id);
        useChatStore.getState().prependMessages(conversation.id, list);
        const last = list[list.length - 1];
        if (last) {
          await messageApi.markRead(conversation.id, last.id);
          useChatStore.getState().clearUnread(conversation.id);
        }
      } catch {
        /* 忽略 */
      }
    })();
  }, [conversation.id]);

  // 新消息自动滚动到底部
  useEffect(() => {
    if (!atTopRef.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages.length]);

  // 切走会话时清理活跃态
  useEffect(() => () => setActive(null), [setActive]);

  const loadMore = async () => {
    if (loadingHistory || messages.length === 0) return;
    setLoadingHistory(true);
    const first = messages[0];
    try {
      const list = await messageApi.history(conversation.id, first.createdAtMillis, first.id);
      const container = scrollRef.current;
      const prevHeight = container?.scrollHeight ?? 0;
      useChatStore.getState().prependMessages(conversation.id, list);
      // 保持滚动位置
      requestAnimationFrame(() => {
        if (container) {
          container.scrollTop = container.scrollHeight - prevHeight;
        }
      });
    } finally {
      setLoadingHistory(false);
    }
  };

  const send = async () => {
    const content = input.trim();
    if (!content || sending) return;
    setSending(true);
    setInput('');
    try {
      const sent = await messageApi.send(conversation.id, content);
      // 服务端扇出也会推送给自己（多端同步），这里直接落位保证即时反馈
      useChatStore.getState().appendMessage(sent);
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    } catch {
      setInput(content); // 失败恢复输入
    } finally {
      setSending(false);
    }
  };

  const recall = async (messageId: string) => {
    try {
      await messageApi.recall(messageId);
      useChatStore.getState().markRecalled(messageId);
    } catch {
      /* 2 分钟外等错误静默 */
    }
  };

  // 已读判定：对方已读位置 >= 我最后一条消息时间 -> 双勾
  const peerReceipt = receipts.find((r) => r.userId !== me?.id);
  const myMessages = messages.filter((m) => m.senderId === me?.id && m.type === 'TEXT');
  const myLastMessage = myMessages[myMessages.length - 1];
  const myLastRead = !!(
    peerReceipt &&
    myLastMessage &&
    peerReceipt.lastReadMessageAtMillis >= myLastMessage.createdAtMillis
  );

  return (
    <div className="flex h-full flex-col">
      {/* 顶栏 */}
      <header className="glass flex items-center gap-3 border-b border-white/8 px-5 py-3">
        <div
          className={`flex h-9 w-9 items-center justify-center rounded-xl text-sm font-bold text-white ${
            conversation.type === 'GROUP'
              ? 'bg-gradient-to-br from-teal-500 to-cyan-600'
              : 'bg-gradient-to-br from-indigo-500 to-purple-600'
          }`}
        >
          {conversation.type === 'GROUP' ? '👥' : conversation.name?.[0]?.toUpperCase()}
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold">{conversation.name}</div>
          <div className="text-[11px] text-white/40">
            {conversation.type === 'GROUP'
              ? `${conversation.memberCount} 人`
              : conversation.peerOnline
                ? '在线'
                : '离线'}
          </div>
        </div>
        <button
          className="rounded-lg px-2 py-1 text-white/40 transition hover:bg-white/10 hover:text-white/80"
          title="会话详情"
          onClick={onToggleDetail}
        >
          ℹ
        </button>
      </header>

      {/* 消息流 */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto px-6 py-4"
        onScroll={(e) => {
          const el = e.currentTarget;
          atTopRef.current = el.scrollTop > 80; // 距顶较远时不打断自动滚底
          if (el.scrollTop === 0) void loadMore();
        }}
      >
        {loadingHistory && (
          <div className="py-2 text-center text-[11px] text-white/30">加载历史消息…</div>
        )}
        {messages.length === 0 && !loadingHistory && (
          <div className="flex h-full items-center justify-center text-xs text-white/25">
            没有消息，说点什么吧 👋
          </div>
        )}
        <MessageList
          messages={messages}
          meId={me?.id ?? ''}
          isDirect={conversation.type === 'DIRECT'}
          myLastMessageId={myLastMessage?.id}
          myLastRead={myLastRead}
          readCount={countRead(conversation, receipts, me?.id ?? '')}
          onRecall={recall}
        />
        <div ref={bottomRef} />
      </div>

      {/* 输入区 */}
      <footer className="glass border-t border-white/8 px-5 py-3">
        <div className="flex items-end gap-3">
          <textarea
            className="max-h-32 min-h-[42px] flex-1 resize-none rounded-xl border border-white/10 bg-white/5 px-4 py-2.5 text-sm outline-none transition focus:border-indigo-400/50"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
          />
          <button
            className="btn-primary rounded-xl px-5 py-2.5 text-sm font-medium text-white"
            disabled={!input.trim() || sending}
            onClick={() => void send()}
          >
            发送
          </button>
        </div>
      </footer>
    </div>
  );
}

function countRead(conversation: ConversationView, receipts: { userId: string; lastReadMessageAtMillis: number }[], meId: string): number {
  return receipts.filter((r) => r.userId !== meId).length;
}

function MessageList({
  messages,
  meId,
  isDirect,
  myLastMessageId,
  myLastRead,
  readCount,
  onRecall,
}: {
  messages: MessageDto[];
  meId: string;
  isDirect: boolean;
  myLastMessageId?: string;
  myLastRead: boolean;
  readCount: number;
  onRecall: (id: string) => void;
}) {
  let lastDay = '';
  return (
    <div className="space-y-1">
      {messages.map((m, i) => {
        const day = new Date(m.createdAtMillis).toDateString();
        const showDay = day !== lastDay;
        lastDay = day;
        const mine = m.senderId === meId;

        if (m.type === 'SYSTEM') {
          return (
            <div key={m.id}>
              {showDay && <DayDivider millis={m.createdAtMillis} />}
              <div className="msg-in py-1.5 text-center text-[11px] text-white/30">{m.content}</div>
            </div>
          );
        }

        return (
          <div key={m.id}>
            {showDay && <DayDivider millis={m.createdAtMillis} />}
            <div className={`msg-in group flex gap-2.5 py-1 ${mine ? 'flex-row-reverse' : ''}`}>
              <div
                className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-xs font-bold text-white ${
                  mine
                    ? 'bg-gradient-to-br from-indigo-500 to-purple-600'
                    : 'bg-gradient-to-br from-slate-500 to-slate-600'
                }`}
              >
                {m.senderNickname?.[0]?.toUpperCase() ?? '?'}
              </div>
              <div className={`flex max-w-[60%] flex-col ${mine ? 'items-end' : 'items-start'}`}>
                {!mine && (
                  <span className="mb-0.5 px-1 text-[11px] text-white/35">{m.senderNickname}</span>
                )}
                {m.recalled ? (
                  <div className="rounded-xl bg-white/5 px-3.5 py-2 text-xs italic text-white/30">
                    {m.senderNickname} 撤回了一条消息
                  </div>
                ) : (
                  <div
                    className={`whitespace-pre-wrap break-words rounded-xl px-3.5 py-2 text-sm leading-relaxed ${
                      mine
                        ? 'bg-gradient-to-br from-indigo-500/90 to-purple-600/90 text-white shadow-md shadow-indigo-500/20'
                        : 'border border-white/8 bg-white/8 text-white/90'
                    }`}
                  >
                    {m.content}
                  </div>
                )}
                <div className="mt-0.5 flex items-center gap-2 px-1">
                  <span className="text-[10px] text-white/25">
                    {new Date(m.createdAtMillis).toLocaleTimeString('zh-CN', {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                  {mine && !m.recalled && isDirect && m.id === myLastMessageId && (
                    <span className="text-[10px] text-indigo-300/80" title={myLastRead ? '已读' : '送达'}>
                      {myLastRead ? '✓✓ 已读' : '✓ 送达'}
                    </span>
                  )}
                  {mine && !m.recalled && !isDirect && m.id === myLastMessageId && (
                    <span className="text-[10px] text-indigo-300/80">已读 {readCount}</span>
                  )}
                  {mine && !m.recalled && (
                    <button
                      className="text-[10px] text-white/20 opacity-0 transition group-hover:opacity-100 hover:text-white/60"
                      title="撤回（2 分钟内）"
                      onClick={() => onRecall(m.id)}
                    >
                      撤回
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function DayDivider({ millis }: { millis: number }) {
  return (
    <div className="flex items-center gap-3 py-3">
      <div className="h-px flex-1 bg-white/8" />
      <span className="text-[10px] text-white/25">
        {new Date(millis).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })}
      </span>
      <div className="h-px flex-1 bg-white/8" />
    </div>
  );
}
