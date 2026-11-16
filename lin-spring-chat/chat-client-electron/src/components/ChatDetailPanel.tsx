import { useEffect, useState } from 'react';
import { conversationApi, errMsg, userApi } from '../api';
import { useChatStore } from '../store';
import type { ConversationView } from '../types';

/** 会话详情：群聊=成员管理（邀请/移除/改名/退群/解散）；单聊=对方资料 */
export default function ChatDetailPanel({
  conversation,
  onRefresh,
  onClose,
}: {
  conversation: ConversationView;
  onRefresh: () => void;
  onClose: () => void;
}) {
  const me = useChatStore((s) => s.me);
  const [members, setMembers] = useState(conversation.members ?? []);
  const [renaming, setRenaming] = useState(false);
  const [newName, setNewName] = useState(conversation.name ?? '');
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteQuery, setInviteQuery] = useState('');
  const [inviteResults, setInviteResults] = useState<{ id: string; nickname: string; username: string }[]>([]);
  const [error, setError] = useState('');

  const isGroup = conversation.type === 'GROUP';
  const isOwner = me?.id === conversation.ownerId;

  useEffect(() => {
    setMembers(conversation.members ?? []);
  }, [conversation.id, conversation.members]);

  useEffect(() => {
    if (inviteOpen && inviteQuery.trim()) {
      const t = setTimeout(async () => {
        try {
          setInviteResults(await userApi.search(inviteQuery));
        } catch {
          /* 忽略 */
        }
      }, 300);
      return () => clearTimeout(t);
    }
    setInviteResults([]);
  }, [inviteOpen, inviteQuery]);

  const doInvite = async (userId: string) => {
    try {
      const updated = await conversationApi.invite(conversation.id, [userId]);
      setMembers(updated.members ?? []);
      useChatStore.getState().upsertConversation(updated);
      onRefresh();
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const doRemoveMember = async (userId: string) => {
    try {
      await conversationApi.removeMember(conversation.id, userId);
      setMembers((list) => list.filter((m) => m.userId !== userId));
      onRefresh();
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const doRename = async () => {
    if (!newName.trim()) return;
    try {
      await conversationApi.rename(conversation.id, newName.trim());
      setRenaming(false);
      onRefresh();
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const doLeave = async () => {
    try {
      await conversationApi.leave(conversation.id);
      useChatStore.getState().removeConversation(conversation.id);
    } catch (e) {
      setError(errMsg(e));
    }
  };

  const doDissolve = async () => {
    try {
      await conversationApi.dissolve(conversation.id);
      useChatStore.getState().removeConversation(conversation.id);
    } catch (e) {
      setError(errMsg(e));
    }
  };

  return (
    <aside className="glass flex w-[280px] shrink-0 flex-col border-l border-white/8">
      <div className="flex items-center justify-between px-4 py-3">
        <span className="text-sm font-medium">{isGroup ? '群信息' : '对方信息'}</span>
        <button
          className="rounded-lg px-2 py-1 text-white/40 transition hover:bg-white/10 hover:text-white/80"
          onClick={onClose}
        >
          ✕
        </button>
      </div>

      {error && (
        <div className="mx-3 mb-2 rounded-lg border border-red-400/30 bg-red-500/10 px-2 py-1.5 text-[11px] text-red-300">
          {error}
        </div>
      )}

      {!isGroup ? (
        <div className="flex flex-col items-center gap-3 px-4 py-8">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-600 text-xl font-bold text-white">
            {conversation.name?.[0]?.toUpperCase()}
          </div>
          <div className="text-base font-medium">{conversation.peerNickname}</div>
          <div
            className={`text-xs ${conversation.peerOnline ? 'text-green-400' : 'text-white/30'}`}
          >
            {conversation.peerOnline ? '● 在线' : '○ 离线'}
          </div>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto px-3">
          {/* 群名 */}
          <div className="glass mb-3 rounded-xl p-3">
            {renaming ? (
              <div className="flex gap-2">
                <input
                  className="min-w-0 flex-1 rounded-lg border border-white/10 bg-white/5 px-2 py-1 text-xs outline-none"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  autoFocus
                />
                <button className="btn-primary rounded-lg px-2 py-1 text-[11px] text-white" onClick={() => void doRename()}>
                  保存
                </button>
              </div>
            ) : (
              <div className="flex items-center justify-between">
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium">{conversation.name}</div>
                  <div className="text-[11px] text-white/35">{members.length || conversation.memberCount} 名成员</div>
                </div>
                {isOwner && (
                  <button
                    className="rounded-lg px-2 py-1 text-[11px] text-indigo-300 hover:bg-white/10"
                    onClick={() => setRenaming(true)}
                  >
                    改名
                  </button>
                )}
              </div>
            )}
          </div>

          {/* 邀请 */}
          {isOwner && (
            <div className="mb-3">
              <button
                className="btn-primary w-full rounded-xl py-2 text-xs font-medium text-white"
                onClick={() => setInviteOpen((v) => !v)}
              >
                {inviteOpen ? '收起邀请' : '+ 邀请成员'}
              </button>
              {inviteOpen && (
                <div className="glass msg-in mt-2 rounded-xl p-2">
                  <input
                    className="w-full rounded-lg border border-white/10 bg-white/5 px-2 py-1.5 text-xs outline-none"
                    placeholder="搜索用户…"
                    value={inviteQuery}
                    onChange={(e) => setInviteQuery(e.target.value)}
                    autoFocus
                  />
                  <div className="mt-2 max-h-40 space-y-1 overflow-y-auto">
                    {inviteResults.map((u) => {
                      const already = members.some((m) => m.userId === u.id);
                      return (
                        <div key={u.id} className="flex items-center justify-between rounded-lg px-2 py-1.5 hover:bg-white/5">
                          <div className="min-w-0">
                            <div className="truncate text-xs">{u.nickname}</div>
                            <div className="truncate text-[10px] text-white/30">@{u.username}</div>
                          </div>
                          <button
                            disabled={already}
                            className="rounded-lg bg-indigo-500/70 px-2 py-0.5 text-[11px] text-white disabled:opacity-30"
                            onClick={() => void doInvite(u.id)}
                          >
                            {already ? '已在群' : '邀请'}
                          </button>
                        </div>
                      );
                    })}
                    {inviteQuery.trim() && inviteResults.length === 0 && (
                      <div className="py-2 text-center text-[11px] text-white/25">无匹配用户</div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* 成员列表 */}
          <div className="space-y-1">
            {members.map((m) => (
              <div key={m.userId} className="group flex items-center gap-2.5 rounded-xl px-2 py-2 hover:bg-white/5">
                <div className="relative">
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-slate-500 to-slate-600 text-[11px] font-bold text-white">
                    {m.nickname?.[0]?.toUpperCase()}
                  </div>
                  <span
                    className={`absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full border-2 border-[#12141c] ${
                      m.online ? 'bg-green-400' : 'bg-white/20'
                    }`}
                  />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-xs">
                    {m.nickname}
                    {m.userId === me?.id && <span className="ml-1 text-white/30">（我）</span>}
                  </div>
                  <div className="text-[10px] text-white/30">
                    {m.role === 'OWNER' ? '群主' : '成员'}
                  </div>
                </div>
                {isOwner && m.userId !== me?.id && (
                  <button
                    className="rounded-lg px-1.5 py-0.5 text-[10px] text-red-300/60 opacity-0 transition hover:bg-red-500/10 hover:text-red-300 group-hover:opacity-100"
                    onClick={() => void doRemoveMember(m.userId)}
                  >
                    移出
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 危险操作 */}
      {isGroup && (
        <div className="space-y-2 px-3 pb-4">
          {!isOwner && (
            <button
              className="w-full rounded-xl border border-red-400/30 bg-red-500/10 py-2 text-xs text-red-300 transition hover:bg-red-500/20"
              onClick={() => void doLeave()}
            >
              退出群聊
            </button>
          )}
          {isOwner && (
            <button
              className="w-full rounded-xl border border-red-400/30 bg-red-500/10 py-2 text-xs text-red-300 transition hover:bg-red-500/20"
              onClick={() => void doDissolve()}
            >
              解散群聊
            </button>
          )}
        </div>
      )}
    </aside>
  );
}
