import { useState, useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import { 
  Send, Hash, Users, LogOut, Settings, Phone, Video, 
  Mic, MicOff, MonitorUp, PhoneOff, User, Bell, Shield, 
  Search, Plus, Compass, Download, Volume2
} from 'lucide-react';
import './index.css';

const socket = io('http://localhost:3001', { autoConnect: false });

function App() {
  const [isJoined, setIsJoined] = useState(false);
  const [username, setUsername] = useState('');
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [activeUsers, setActiveUsers] = useState([]);
  const [activeChannel, setActiveChannel] = useState('general');
  const [activeServer, setActiveServer] = useState('DevChat');
  
  // Modals / UI States
  const [showSettings, setShowSettings] = useState(false);
  const [showProfile, setShowProfile] = useState(false);
  const [inCall, setInCall] = useState(false);
  const [micMuted, setMicMuted] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);

  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    socket.on('connect', () => console.log('WebSocket connected'));
    socket.on('message_history', (history) => setMessages(history));
    socket.on('receive_message', (message) => setMessages((prev) => [...prev, message]));
    socket.on('active_users', (users) => setActiveUsers(users));

    return () => {
      socket.off('connect');
      socket.off('message_history');
      socket.off('receive_message');
      socket.off('active_users');
    };
  }, []);

  const handleJoin = (e) => {
    e.preventDefault();
    if (username.trim()) {
      socket.connect();
      socket.emit('register_user', username);
      setIsJoined(true);
    }
  };

  const handleSendMessage = (e) => {
    e.preventDefault();
    if (inputMessage.trim()) {
      // Si hubieran multiples canales, el payload incluiría el canal
      socket.emit('send_message', { content: inputMessage, channel: activeChannel });
      setInputMessage('');
    }
  };

  const formatTime = (iso) => {
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      handleSendMessage(e);
    }
  };

  if (!isJoined) {
    return (
      <div className="modal-overlay" style={{ background: 'var(--bg-base)' }}>
        <div className="modal-content" style={{ maxWidth: '480px', background: 'var(--bg-dark)' }}>
          <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
            <h1 className="modal-title" style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>Welcome back!</h1>
            <p style={{ color: 'var(--text-secondary)' }}>We're so excited to see you again!</p>
          </div>
          <form onSubmit={handleJoin}>
            <div className="form-group">
              <label className="form-label">USERNAME</label>
              <input
                type="text"
                className="form-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="off"
              />
            </div>
            <button type="submit" className="btn btn-primary">Log In</button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="app-layout">
      {/* 1. Servers Bar (Leftmost) */}
      <div className="servers-bar">
        <div className="server-icon" style={{ backgroundColor: 'var(--primary)', color: 'white' }}>
          DC
        </div>
        <div className="server-divider"></div>
        <div className={`server-icon ${activeServer === 'DevChat' ? 'active' : ''}`} onClick={() => setActiveServer('DevChat')}>
          <img src="https://ui-avatars.com/api/?name=DC&background=2B2D31&color=fff&size=48" alt="Server" style={{ borderRadius: 'inherit' }} />
        </div>
        <div className={`server-icon ${activeServer === 'Gaming' ? 'active' : ''}`} onClick={() => setActiveServer('Gaming')}>
          <img src="https://ui-avatars.com/api/?name=GM&background=2B2D31&color=fff&size=48" alt="Server" style={{ borderRadius: 'inherit' }} />
        </div>
        <div className="server-icon" style={{ backgroundColor: 'var(--bg-light)', color: 'var(--success)' }}>
          <Plus size={24} />
        </div>
        <div className="server-icon" style={{ backgroundColor: 'var(--bg-light)', color: 'var(--text-success)' }}>
          <Compass size={24} />
        </div>
        <div className="server-divider" style={{ marginTop: 'auto' }}></div>
        <div className="server-icon" style={{ backgroundColor: 'var(--bg-light)' }}>
          <Download size={24} />
        </div>
      </div>

      {/* 2. Channels Sidebar */}
      <div className="channels-sidebar">
        <div className="sidebar-header">
          <span>{activeServer} Server</span>
          <Settings size={18} />
        </div>
        
        <div className="channels-list">
          <div className="channel-category">Text Channels</div>
          <div className={`channel-item ${activeChannel === 'general' ? 'active' : ''}`} onClick={() => setActiveChannel('general')}>
            <Hash size={18} className="channel-icon" /> general
          </div>
          <div className={`channel-item ${activeChannel === 'development' ? 'active' : ''}`} onClick={() => setActiveChannel('development')}>
            <Hash size={18} className="channel-icon" /> development
          </div>
          <div className={`channel-item ${activeChannel === 'random' ? 'active' : ''}`} onClick={() => setActiveChannel('random')}>
            <Hash size={18} className="channel-icon" /> random
          </div>

          <div className="channel-category">Voice Channels</div>
          <div className="channel-item" onClick={() => setInCall(true)}>
            <Volume2 size={18} className="channel-icon" /> General Voice
          </div>
          <div className="channel-item" onClick={() => setInCall(true)}>
            <Volume2 size={18} className="channel-icon" /> Gaming Lounge
          </div>
        </div>

        {/* User Panel (Bottom Left) */}
        <div className="user-panel">
          <div className="user-info" onClick={() => setShowProfile(true)}>
            <div className="avatar-wrapper">
              <div className="avatar">{username.charAt(0).toUpperCase()}</div>
              <div className="status-dot"></div>
            </div>
            <div className="user-text">
              <span className="username-display">{username}</span>
              <span className="user-status-text">Online</span>
            </div>
          </div>
          <div className="panel-actions">
            <button className="icon-btn" onClick={() => setMicMuted(!micMuted)}>
              {micMuted ? <MicOff size={18} color="var(--danger)" /> : <Mic size={18} />}
            </button>
            <button className="icon-btn" onClick={() => setShowSettings(true)}>
              <Settings size={18} />
            </button>
          </div>
        </div>
      </div>

      {/* 3. Main Chat Area */}
      <div className="main-area">
        <div className="chat-header">
          <div className="header-title">
            <Hash size={24} style={{ color: 'var(--text-muted)', marginRight: '8px' }} />
            {activeChannel}
          </div>
          <div className="header-actions">
            <Phone size={20} style={{ color: 'var(--text-muted)', cursor: 'pointer' }} onClick={() => setInCall(true)} />
            <Video size={20} style={{ color: 'var(--text-muted)', cursor: 'pointer' }} onClick={() => setInCall(true)} />
            <Users size={20} style={{ color: 'var(--text-muted)', cursor: 'pointer' }} />
            <div style={{ position: 'relative' }}>
              <input type="text" placeholder="Search" style={{ background: 'var(--bg-darker)', border: 'none', color: 'white', padding: '4px 8px', borderRadius: '4px', width: '144px', outline: 'none' }} />
              <Search size={14} style={{ position: 'absolute', right: '8px', top: '6px', color: 'var(--text-muted)' }} />
            </div>
          </div>
        </div>

        {inCall && (
          <div className="call-container">
            <div className="video-grid">
              <div className="video-participant speaking">
                <div className="participant-avatar">{username.charAt(0).toUpperCase()}</div>
                <div className="participant-name">{username}</div>
              </div>
              {activeUsers.filter(u => u.name !== username).slice(0, 2).map((user, i) => (
                <div key={i} className="video-participant">
                  <div className="participant-avatar" style={{ backgroundColor: 'var(--warning)' }}>
                    {user.name.charAt(0).toUpperCase()}
                  </div>
                  <div className="participant-name">{user.name}</div>
                </div>
              ))}
            </div>
            <div className="call-controls">
              <button className="call-btn" onClick={() => setMicMuted(!micMuted)}>
                {micMuted ? <MicOff size={20} color="var(--danger)" /> : <Mic size={20} />}
              </button>
              <button className="call-btn"><Video size={20} /></button>
              <button className="call-btn"><MonitorUp size={20} /></button>
              <button className="call-btn danger" onClick={() => setInCall(false)}>
                <PhoneOff size={20} />
              </button>
            </div>
          </div>
        )}

        <div className="messages-wrapper">
          <div className="chat-content">
            <div className="messages-scroll">
              <div style={{ padding: '2rem 1rem', marginTop: 'auto' }}>
                <div style={{ width: 68, height: 68, borderRadius: '50%', backgroundColor: 'var(--bg-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '1rem' }}>
                  <Hash size={40} color="white" />
                </div>
                <h1 style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>Welcome to #{activeChannel}!</h1>
                <p style={{ color: 'var(--text-secondary)' }}>This is the start of the #{activeChannel} channel.</p>
              </div>

              {messages.map((msg, index) => {
                const showHeader = index === 0 || messages[index - 1].sender_id !== msg.sender_id || 
                  (new Date(msg.timestamp) - new Date(messages[index - 1].timestamp) > 300000);
                
                return (
                  <div key={msg.id || index} className="message-group">
                    {showHeader ? (
                      <div className="msg-avatar" onClick={() => setSelectedUser(msg.sender_name)}>
                        {msg.sender_name.charAt(0).toUpperCase()}
                      </div>
                    ) : (
                      <div style={{ width: '40px', marginRight: '1rem', flexShrink: 0, textAlign: 'center', fontSize: '0.65rem', color: 'transparent', paddingTop: '0.2rem' }} className="msg-time-hover">
                        {new Date(msg.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                      </div>
                    )}
                    <div className="msg-body">
                      {showHeader && (
                        <div className="msg-header">
                          <span className="msg-author" onClick={() => setSelectedUser(msg.sender_name)}>{msg.sender_name}</span>
                          <span className="msg-timestamp">{formatTime(msg.timestamp)}</span>
                        </div>
                      )}
                      <div className="msg-content">{msg.content}</div>
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            <div className="input-area">
              <div className="input-wrapper">
                <button style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', marginRight: '1rem', padding: '0.2rem' }}>
                  <Plus size={24} />
                </button>
                <textarea
                  className="input-field"
                  placeholder={`Message #${activeChannel}`}
                  value={inputMessage}
                  onChange={(e) => setInputMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  rows={1}
                />
                <div className="input-actions">
                  <button style={{ background: 'transparent', border: 'none', color: 'var(--primary)', cursor: 'pointer', opacity: inputMessage.trim() ? 1 : 0.5 }}>
                    <Send size={20} onClick={handleSendMessage} />
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* 4. Members Sidebar (Rightmost) */}
          <div className="members-sidebar">
            <div className="members-list">
              <div className="member-category">Online — {activeUsers.length}</div>
              {activeUsers.map(user => (
                <div key={user.id} className="member-item" onClick={() => setSelectedUser(user.name)}>
                  <div className="avatar-wrapper">
                    <div className="avatar" style={{ width: 32, height: 32 }}>
                      {user.name.charAt(0).toUpperCase()}
                    </div>
                    <div className="status-dot"></div>
                  </div>
                  <span style={{ color: 'var(--text-secondary)' }}>{user.name}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Settings Modal */}
      {showSettings && (
        <div className="modal-overlay" onClick={() => setShowSettings(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={{ display: 'flex', maxWidth: '800px', height: '80vh', padding: 0 }}>
            <div style={{ width: '220px', backgroundColor: 'var(--bg-darker)', padding: '2rem 1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-secondary)', paddingLeft: '0.5rem', marginBottom: '0.5rem' }}>USER SETTINGS</div>
              <div className="channel-item active"><User size={16} className="channel-icon"/> My Account</div>
              <div className="channel-item"><Shield size={16} className="channel-icon"/> Privacy & Safety</div>
              <div className="channel-item"><Bell size={16} className="channel-icon"/> Notifications</div>
              <div style={{ borderTop: '1px solid var(--border)', margin: '0.5rem 0' }}></div>
              <div className="channel-item" style={{ color: 'var(--danger)' }} onClick={() => {
                socket.disconnect();
                window.location.reload();
              }}>
                <LogOut size={16} className="channel-icon"/> Log Out
              </div>
            </div>
            <div style={{ flex: 1, padding: '2rem', backgroundColor: 'var(--bg-base)', position: 'relative' }}>
              <button style={{ position: 'absolute', top: '1rem', right: '1rem', background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }} onClick={() => setShowSettings(false)}>
                ✕ ESC
              </button>
              <h2 style={{ marginBottom: '1.5rem' }}>My Account</h2>
              <div style={{ backgroundColor: 'var(--bg-dark)', borderRadius: '8px', padding: '1rem' }}>
                <div style={{ height: '100px', backgroundColor: 'var(--primary)', borderRadius: '8px 8px 0 0', margin: '-1rem -1rem 1rem -1rem' }}></div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: '-40px' }}>
                  <div className="avatar" style={{ width: 80, height: 80, fontSize: '2rem', border: '6px solid var(--bg-dark)' }}>
                    {username.charAt(0).toUpperCase()}
                  </div>
                  <button className="btn btn-primary" style={{ width: 'auto' }}>Edit User Profile</button>
                </div>
                <div style={{ marginTop: '1rem', backgroundColor: 'var(--bg-darker)', borderRadius: '8px', padding: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem' }}>
                    <div>
                      <div className="form-label">USERNAME</div>
                      <div>{username}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Profile Popout */}
      {selectedUser && (
        <div className="modal-overlay" style={{ background: 'transparent' }} onClick={() => setSelectedUser(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={{ width: '300px', padding: 0, overflow: 'hidden', position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', animation: 'scaleIn 0.2s ease-out' }}>
            <div style={{ height: '60px', backgroundColor: 'var(--primary)' }}></div>
            <div style={{ padding: '1rem', backgroundColor: 'var(--bg-dark)', position: 'relative' }}>
              <div className="avatar" style={{ width: 80, height: 80, fontSize: '2rem', border: '6px solid var(--bg-dark)', position: 'absolute', top: '-40px' }}>
                {selectedUser.charAt(0).toUpperCase()}
              </div>
              <div style={{ marginTop: '40px', backgroundColor: 'var(--bg-darker)', borderRadius: '8px', padding: '1rem' }}>
                <h3 style={{ margin: 0 }}>{selectedUser}</h3>
                <div style={{ borderTop: '1px solid var(--border)', margin: '0.5rem 0' }}></div>
                <div className="form-label">MEMBER SINCE</div>
                <div style={{ fontSize: '0.85rem' }}>{new Date().toLocaleDateString()}</div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
