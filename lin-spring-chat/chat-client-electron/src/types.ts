export interface UserView {
  id: string;
  username: string;
  nickname: string;
}

export interface UserItem {
  id: string;
  username: string;
  nickname: string;
  online: boolean;
}

export interface AuthResponse {
  token: string;
  user: UserView;
}

export interface MemberView {
  userId: string;
  username: string;
  nickname: string;
  role: 'OWNER' | 'MEMBER';
  online: boolean;
}

export interface ConversationView {
  id: string;
  type: 'DIRECT' | 'GROUP';
  name: string;
  ownerId: string | null;
  memberCount: number;
  lastMessagePreview: string | null;
  lastMessageAtMillis: number | null;
  peerId: string | null;
  peerNickname: string | null;
  peerOnline: boolean;
  members: MemberView[] | null;
  unreadCount: number;
}

export interface MessageDto {
  id: string;
  conversationId: string;
  conversationType: 'DIRECT' | 'GROUP';
  senderId: string;
  senderNickname: string;
  type: 'TEXT' | 'SYSTEM';
  content: string;
  createdAtMillis: number;
  recalled: boolean;
}

export interface PresenceEvent {
  userId: string;
  online: boolean;
  timestampMillis: number;
}

export interface ReadReceiptEvent {
  conversationId: string;
  userId: string;
  lastReadMessageId: string;
  lastReadMessageAtMillis: number;
  timestampMillis: number;
}

export interface ConversationEvent {
  conversationId: string;
  action:
    | 'CREATED'
    | 'MEMBER_ADDED'
    | 'MEMBER_REMOVED'
    | 'MEMBER_LEFT'
    | 'RENAMED'
    | 'DISSOLVED'
    | 'MESSAGE_RECALLED';
  actorId: string;
  refId: string | null;
  timestampMillis: number;
}

export interface ReadStatusView {
  readCount: number;
  readByNicknames: string[];
}
