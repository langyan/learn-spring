import { useEffect, useState } from 'react';
import { setToken } from './api';
import { connectStomp, disconnectStomp } from './stomp';
import { useChatStore } from './store';
import LoginPage from './components/LoginPage';
import MainLayout from './components/MainLayout';

export default function App() {
  const me = useChatStore((s) => s.me);
  const [token, setAppToken] = useState<string | null>(null);

  useEffect(() => {
    // 登录/注册后建立实时连接
    if (me && token) {
      connectStomp(token);
    }
    return () => disconnectStomp();
  }, [me, token]);

  const handleLogout = () => {
    disconnectStomp();
    setToken(null);
    setAppToken(null);
    useChatStore.getState().reset();
  };

  if (!me || !token) {
    return (
      <LoginPage
        onAuthed={(t, user) => {
          setToken(t);
          setAppToken(t);
          useChatStore.getState().setMe(user);
        }}
      />
    );
  }

  return <MainLayout onLogout={handleLogout} />;
}
