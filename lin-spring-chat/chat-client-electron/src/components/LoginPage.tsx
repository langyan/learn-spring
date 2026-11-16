import { useState } from 'react';
import { authApi, errMsg } from '../api';
import type { UserView } from '../types';

export default function LoginPage({
  onAuthed,
}: {
  onAuthed: (token: string, user: UserView) => void;
}) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res =
        mode === 'login'
          ? await authApi.login({ username, password })
          : await authApi.register({ username, password, nickname });
      onAuthed(res.token, res.user);
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-bg flex h-full items-center justify-center">
      <div className="glass msg-in w-[400px] rounded-2xl p-8 shadow-2xl">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-500 text-2xl shadow-lg">
            💬
          </div>
          <h1 className="bg-gradient-to-r from-indigo-300 to-purple-300 bg-clip-text text-2xl font-bold text-transparent">
            LinChat
          </h1>
          <p className="mt-1 text-sm text-white/40">
            {mode === 'login' ? '登录你的账号' : '创建新账号'}
          </p>
        </div>

        <form onSubmit={submit} className="space-y-4">
          <input
            className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-2.5 text-sm outline-none transition focus:border-indigo-400/60 focus:bg-white/10"
            placeholder="用户名"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
          />
          <input
            className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-2.5 text-sm outline-none transition focus:border-indigo-400/60 focus:bg-white/10"
            placeholder="密码"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {mode === 'register' && (
            <input
              className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-2.5 text-sm outline-none transition focus:border-indigo-400/60 focus:bg-white/10"
              placeholder="昵称（可选）"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
            />
          )}

          {error && (
            <div className="rounded-lg border border-red-400/30 bg-red-500/10 px-3 py-2 text-xs text-red-300">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading || !username || !password}
            className="btn-primary w-full rounded-xl py-2.5 text-sm font-medium text-white"
          >
            {loading ? '请稍候…' : mode === 'login' ? '登 录' : '注 册'}
          </button>
        </form>

        <div className="mt-5 text-center text-xs text-white/40">
          {mode === 'login' ? '还没有账号？' : '已有账号？'}
          <button
            className="ml-1 text-indigo-300 transition hover:text-indigo-200"
            onClick={() => {
              setMode(mode === 'login' ? 'register' : 'login');
              setError('');
            }}
          >
            {mode === 'login' ? '注册' : '登录'}
          </button>
        </div>
      </div>
    </div>
  );
}
