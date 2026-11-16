import { useEffect, useState } from 'react';
import { conversationApi, errMsg, userApi } from '../api';
import type { ConversationView, UserItem } from '../types';

/** 发起聊天：搜索用户 -> 单聊 / 勾选多人 -> 建群（Telegram 风格直接开聊） */
export default function NewChatModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (conv: ConversationView) => void;
}) {
  const [mode, setMode] = useState<'direct' | 'group'>('direct');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserItem[]>([]);
  const [selected, setSelected] = useState<Map<string, UserItem>>(new Map());
  const [groupName, setGroupName] = useState('');
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    const t = setTimeout(async () => {
      try {
        setResults(await userApi.search(query));
      } catch {
        /* 忽略 */
      }
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  const toggle = (u: UserItem) => {
    setMode('group');
    setSelected((prev) => {
      const next = new Map(prev);
      if (next.has(u.id)) {
        next.delete(u.id);
      } else {
        next.set(u.id, u);
      }
      return next;
    });
  };

  const startDirect = async (u: UserItem) => {
    try {
      const conv = await conversationApi.direct(u.id);
      onCreated(conv);
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const createGroup = async () => {
    if (!groupName.trim() || selected.size === 0) return;
    setCreating(true);
    try {
      const conv = await conversationApi.createGroup(
        groupName.trim(),
        [...selected.keys()],
      );
      onCreated(conv);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setCreating(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="glass msg-in w-[460px] rounded-2xl p-5 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold">发起聊天</h2>
          <button className="rounded-lg px-2 py-1 text-white/40 hover:bg-white/10" onClick={onClose}>
            ✕
          </button>
        </div>

        {/* 模式切换 */}
        <div className="mb-3 flex gap-1 rounded-xl bg-white/5 p-1 text-xs">
          <button
            className={`flex-1 rounded-lg py-1.5 transition ${mode === 'direct' ? 'bg-white/10 font-medium' : 'text-white/40'}`}
            onClick={() => setMode('direct')}
          >
            单聊
          </button>
          <button
            className={`flex-1 rounded-lg py-1.5 transition ${mode === 'group' ? 'bg-white/10 font-medium' : 'text-white/40'}`}
            onClick={() => setMode('group')}
          >
            建群（已选 {selected.size}）
          </button>
        </div>

        <input
          className="w-full rounded-xl border border-white/10 bg-white/5 px-3.5 py-2.5 text-sm outline-none transition focus:border-indigo-400/50"
          placeholder="搜索用户名或昵称…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          autoFocus
        />

        {/* 搜索结果 */}
        <div className="mt-3 max-h-56 space-y-1 overflow-y-auto">
          {results.map((u) => {
            const checked = selected.has(u.id);
            return (
              <div
                key={u.id}
                className={`flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2 transition ${
                  checked ? 'bg-indigo-500/15' : 'hover:bg-white/5'
                }`}
                onClick={() => (mode === 'direct' ? void startDirect(u) : toggle(u))}
              >
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-slate-500 to-slate-600 text-xs font-bold text-white">
                  {u.nickname?.[0]?.toUpperCase()}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm">{u.nickname}</div>
                  <div className="truncate text-[11px] text-white/30">@{u.username}</div>
                </div>
                {mode === 'group' && (
                  <span
                    className={`flex h-4.5 w-4.5 items-center justify-center rounded border text-[10px] ${
                      checked
                        ? 'border-indigo-400 bg-indigo-500 text-white'
                        : 'border-white/20'
                    }`}
                    style={{ width: 18, height: 18 }}
                  >
                    {checked ? '✓' : ''}
                  </span>
                )}
                <span
                  className={`h-2 w-2 rounded-full ${u.online ? 'bg-green-400' : 'bg-white/15'}`}
                />
              </div>
            );
          })}
          {query.trim() && results.length === 0 && (
            <div className="py-6 text-center text-xs text-white/25">无匹配用户</div>
          )}
        </div>

        {/* 建群区 */}
        {mode === 'group' && (
          <div className="mt-3 space-y-2 border-t border-white/8 pt-3">
            {selected.size > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {[...selected.values()].map((u) => (
                  <span
                    key={u.id}
                    className="flex items-center gap-1 rounded-full bg-indigo-500/20 px-2 py-0.5 text-[11px] text-indigo-200"
                  >
                    {u.nickname}
                    <button className="text-indigo-300/60 hover:text-white" onClick={() => toggle(u)}>
                      ✕
                    </button>
                  </span>
                ))}
              </div>
            )}
            <div className="flex gap-2">
              <input
                className="min-w-0 flex-1 rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-sm outline-none focus:border-indigo-400/50"
                placeholder="群名称"
                value={groupName}
                onChange={(e) => setGroupName(e.target.value)}
              />
              <button
                className="btn-primary rounded-xl px-4 py-2 text-sm text-white"
                disabled={!groupName.trim() || selected.size === 0 || creating}
                onClick={() => void createGroup()}
              >
                {creating ? '创建中…' : '创建群聊'}
              </button>
            </div>
          </div>
        )}

        {error && (
          <div className="mt-3 rounded-lg border border-red-400/30 bg-red-500/10 px-3 py-2 text-xs text-red-300">
            {error}
          </div>
        )}
      </div>
    </div>
  );
}
