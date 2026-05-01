import { useState, useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import EmojiPicker from 'emoji-picker-react';
import { 
  Send, Hash, Users, LogOut, Settings, Phone, Video, 
  Mic, MicOff, PhoneOff, User, Shield, 
  Search, Plus, Volume2, Smile, Image as ImageIcon
} from 'lucide-react';
import './index.css';

const socket = io('http://localhost:3001', { autoConnect: false });

function App() {
  const [isJoined, setIsJoined] = useState(false);
  const [username, setUsername] = useState('');
  const [newUsernameInput, setNewUsernameInput] = useState('');
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [activeUsers, setActiveUsers] = useState([]);
  const [activeChannel, setActiveChannel] = useState('general');
  const [directMessages, setDirectMessages] = useState([]); // lista de usuarios con los que hay DM
  
  // UI States
  const [showSettings, setShowSettings] = useState(false);
  const [showProfile, setShowProfile] = useState(null);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [typingUsers, setTypingUsers] = useState([]);
  
  // Voice Chat States
  const [inVoiceChannel, setInVoiceChannel] = useState(null);
  const [micMuted, setMicMuted] = useState(false);
  const [voiceUsers, setVoiceUsers] = useState([]); // Users in current voice chat for WebRTC
  const [globalVoiceChannels, setGlobalVoiceChannels] = useState({}); // To display who is in which channel
  
  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);
  const localStreamRef = useRef(null);
  const peerConnectionsRef = useRef({}); 
  const audioRefs = useRef({});
  const typingTimeoutRef = useRef(null);

  const configuration = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (isJoined) {
      setMessages([]);
      socket.emit('join_channel', activeChannel);
      setShowEmojiPicker(false);
    }
  }, [activeChannel, isJoined]);

  useEffect(() => {
    socket.on('connect', () => console.log('WebSocket connected'));
    
    socket.on('message_history', (history) => {
      // Parsear content si es JSON
      const parsed = history.map(msg => {
        try {
          const contentObj = JSON.parse(msg.content);
          return { ...msg, content: contentObj.text, type: contentObj.type };
        } catch(e) {
          return { ...msg, type: 'text' }; // Para mensajes antiguos en BD
        }
      });
      setMessages(parsed);
    });
    
    socket.on('receive_message', (message) => {
      if (message.channel === activeChannel) {
        try {
          const contentObj = JSON.parse(message.content);
          message.content = contentObj.text;
          message.type = contentObj.type;
        } catch(e) {
          message.type = 'text';
        }
        setMessages((prev) => [...prev, message]);
        // Quitar al usuario que escribio de "Typing..."
        setTypingUsers(prev => prev.filter(u => u !== message.sender_name));
      }
    });
    
    socket.on('active_users', (users) => setActiveUsers(users));
    
    socket.on('username_changed', ({ id, newName }) => {
      setActiveUsers(prev => prev.map(u => u.id === id ? { ...u, name: newName } : u));
      if (id === socket.id) setUsername(newName);
    });

    socket.on('user_typing', ({ username, channel }) => {
      if (channel === activeChannel) {
        setTypingUsers(prev => {
          if (!prev.includes(username)) return [...prev, username];
          return prev;
        });
        setTimeout(() => {
          setTypingUsers(prev => prev.filter(u => u !== username));
        }, 3000);
      }
    });

    socket.on('voice_channels_update', (vcState) => {
      setGlobalVoiceChannels(vcState);
    });

    // ----- WEBRTC VOICE LOGIC -----
    socket.on('user_joined_voice', async ({ id, name }) => {
      setVoiceUsers(prev => [...prev, { id, name }]);
      const pc = createPeerConnection(id);
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      socket.emit('webrtc_offer', { target: id, sdp: pc.localDescription });
    });

    socket.on('webrtc_offer', async ({ sdp, caller, callerName }) => {
      setVoiceUsers(prev => {
        if (!prev.find(u => u.id === caller)) return [...prev, { id: caller, name: callerName }];
        return prev;
      });
      const pc = createPeerConnection(caller);
      await pc.setRemoteDescription(new RTCSessionDescription(sdp));
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      socket.emit('webrtc_answer', { target: caller, sdp: pc.localDescription });
    });

    socket.on('webrtc_answer', async ({ sdp, callee }) => {
      const pc = peerConnectionsRef.current[callee];
      if (pc) await pc.setRemoteDescription(new RTCSessionDescription(sdp));
    });

    socket.on('webrtc_ice_candidate', ({ candidate, sender }) => {
      const pc = peerConnectionsRef.current[sender];
      if (pc && candidate) pc.addIceCandidate(new RTCIceCandidate(candidate));
    });

    socket.on('user_left_voice', (id) => {
      setVoiceUsers(prev => prev.filter(u => u.id !== id));
      if (peerConnectionsRef.current[id]) {
        peerConnectionsRef.current[id].close();
        delete peerConnectionsRef.current[id];
      }
      if (audioRefs.current[id]) {
        audioRefs.current[id].pause();
        audioRefs.current[id].srcObject = null;
        delete audioRefs.current[id];
      }
    });

    return () => {
      socket.off('connect');
      socket.off('message_history');
      socket.off('receive_message');
      socket.off('active_users');
      socket.off('username_changed');
      socket.off('user_typing');
      socket.off('voice_channels_update');
      socket.off('user_joined_voice');
      socket.off('webrtc_offer');
      socket.off('webrtc_answer');
      socket.off('webrtc_ice_candidate');
      socket.off('user_left_voice');
    };
  }, [activeChannel]);

  const createPeerConnection = (targetId) => {
    const pc = new RTCPeerConnection(configuration);
    peerConnectionsRef.current[targetId] = pc;

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach(track => {
        pc.addTrack(track, localStreamRef.current);
      });
    }

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        socket.emit('webrtc_ice_candidate', { target: targetId, candidate: event.candidate });
      }
    };

    pc.ontrack = (event) => {
      let audio = audioRefs.current[targetId];
      if (!audio) {
        audio = new Audio();
        audio.autoplay = true;
        audioRefs.current[targetId] = audio;
      }
      audio.srcObject = event.streams[0];
    };

    return pc;
  };

  const joinVoiceChannel = async (channelName) => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      localStreamRef.current = stream;
      setInVoiceChannel(channelName);
      setVoiceUsers([{ id: socket.id, name: username }]);
      socket.emit('join_voice', channelName);
    } catch (err) {
      alert("Error al acceder al micrófono. Da permisos en el navegador.");
    }
  };

  const leaveVoiceChannel = () => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach(track => track.stop());
      localStreamRef.current = null;
    }
    Object.values(peerConnectionsRef.current).forEach(pc => pc.close());
    peerConnectionsRef.current = {};
    
    Object.values(audioRefs.current).forEach(a => { a.pause(); a.srcObject = null; });
    audioRefs.current = {};

    socket.emit('leave_voice', inVoiceChannel);
    setInVoiceChannel(null);
    setVoiceUsers([]);
  };

  const toggleMic = () => {
    if (localStreamRef.current) {
      localStreamRef.current.getAudioTracks().forEach(track => {
        track.enabled = !track.enabled;
      });
      setMicMuted(!localStreamRef.current.getAudioTracks()[0].enabled);
    }
  };

  const handleJoin = (e) => {
    e.preventDefault();
    if (username.trim()) {
      socket.connect();
      socket.emit('register_user', username);
      setNewUsernameInput(username);
      setIsJoined(true);
    }
  };

  const handleSendMessage = (e) => {
    e?.preventDefault();
    if (inputMessage.trim()) {
      socket.emit('send_message', { content: inputMessage, channel: activeChannel, type: 'text' });
      setInputMessage('');
      setShowEmojiPicker(false);
    }
  };

  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const base64Data = event.target.result;
        socket.emit('send_message', { content: base64Data, channel: activeChannel, type: 'image' });
      };
      reader.readAsDataURL(file);
    }
  };

  const handleTyping = (e) => {
    setInputMessage(e.target.value);
    
    if (!typingTimeoutRef.current) {
      socket.emit('typing', { channel: activeChannel });
      typingTimeoutRef.current = setTimeout(() => {
        typingTimeoutRef.current = null;
      }, 2000);
    }
  };

  const onEmojiClick = (emojiObject) => {
    setInputMessage(prev => prev + emojiObject.emoji);
  };

  const startDM = (targetUser) => {
    if (targetUser === username) return;
    const dmChannel = `dm_${[username, targetUser].sort().join('_')}`;
    if (!directMessages.includes(targetUser)) {
      setDirectMessages([...directMessages, targetUser]);
    }
    setActiveChannel(dmChannel);
    setShowProfile(null);
  };

  const formatTime = (iso) => {
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage(e);
    }
  };

  const saveSettings = () => {
    if (newUsernameInput.trim() && newUsernameInput !== username) {
      socket.emit('update_username', newUsernameInput);
    }
    setShowSettings(false);
  };

  if (!isJoined) {
    return (
      <div className="modal-overlay" style={{ background: 'var(--bg-base)' }}>
        <div className="modal-content" style={{ maxWidth: '480px', background: 'var(--bg-dark)' }}>
          <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
            <h1 className="modal-title" style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>Bienvenido de nuevo</h1>
            <p style={{ color: 'var(--text-secondary)' }}>¡Entra para empezar a chatear!</p>
          </div>
          <form onSubmit={handleJoin}>
            <div className="form-group">
              <label className="form-label">USUARIO</label>
              <input
                type="text"
                className="form-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="off"
              />
            </div>
            <button type="submit" className="btn btn-primary">Entrar</button>
          </form>
        </div>
      </div>
    );
  }

  const isDM = activeChannel.startsWith('dm_');
  const dmTarget = isDM ? activeChannel.replace('dm_', '').replace(username, '').replace('_', '') : null;

  return (
    <div className="app-layout">
      {/* Channels Sidebar */}
      <div className="channels-sidebar">
        <div className="sidebar-header">
          <span>Servidor Principal</span>
        </div>
        
        <div className="channels-list">
          <div className="channel-category">Canales de Texto</div>
          <div className={`channel-item ${activeChannel === 'general' ? 'active' : ''}`} onClick={() => setActiveChannel('general')}>
            <Hash size={18} className="channel-icon" /> general
          </div>
          <div className={`channel-item ${activeChannel === 'desarrollo' ? 'active' : ''}`} onClick={() => setActiveChannel('desarrollo')}>
            <Hash size={18} className="channel-icon" /> desarrollo
          </div>
          <div className={`channel-item ${activeChannel === 'ofitopic' ? 'active' : ''}`} onClick={() => setActiveChannel('ofitopic')}>
            <Hash size={18} className="channel-icon" /> ofitopic
          </div>

          <div className="channel-category">Canales de Voz (Clic para unirte)</div>
          
          {/* Voz General */}
          <div className={`channel-item ${inVoiceChannel === 'Voz General' ? 'active' : ''}`} onClick={() => !inVoiceChannel ? joinVoiceChannel('Voz General') : null}>
            <Volume2 size={18} className="channel-icon" style={{ color: inVoiceChannel === 'Voz General' ? 'var(--success)' : '' }} /> Voz General
          </div>
          {globalVoiceChannels['Voz General'] && globalVoiceChannels['Voz General'].map(u => (
            <div key={u.id} style={{ display: 'flex', alignItems: 'center', padding: '0.2rem 1rem 0.2rem 2rem', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              <div style={{ width: 20, height: 20, borderRadius: '50%', backgroundColor: 'var(--primary)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '0.5rem', fontSize: '10px' }}>
                {u.name.charAt(0).toUpperCase()}
              </div>
              {u.name}
            </div>
          ))}

          {/* Juegos */}
          <div className={`channel-item ${inVoiceChannel === 'Juegos' ? 'active' : ''}`} onClick={() => !inVoiceChannel ? joinVoiceChannel('Juegos') : null}>
            <Volume2 size={18} className="channel-icon" style={{ color: inVoiceChannel === 'Juegos' ? 'var(--success)' : '' }} /> Juegos
          </div>
          {globalVoiceChannels['Juegos'] && globalVoiceChannels['Juegos'].map(u => (
            <div key={u.id} style={{ display: 'flex', alignItems: 'center', padding: '0.2rem 1rem 0.2rem 2rem', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              <div style={{ width: 20, height: 20, borderRadius: '50%', backgroundColor: 'var(--primary)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: '0.5rem', fontSize: '10px' }}>
                {u.name.charAt(0).toUpperCase()}
              </div>
              {u.name}
            </div>
          ))}

          {directMessages.length > 0 && (
            <>
              <div className="channel-category">Mensajes Privados</div>
              {directMessages.map(dm => {
                const dmChannel = `dm_${[username, dm].sort().join('_')}`;
                return (
                  <div key={dm} className={`channel-item ${activeChannel === dmChannel ? 'active' : ''}`} onClick={() => setActiveChannel(dmChannel)}>
                    <div style={{ width: 18, height: 18, borderRadius: '50%', backgroundColor: 'var(--primary)', marginRight: '0.5rem', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '10px', color: 'white', fontWeight: 'bold' }}>
                      {dm.charAt(0).toUpperCase()}
                    </div>
                    {dm}
                  </div>
                );
              })}
            </>
          )}
        </div>

        {/* User Panel */}
        <div className="user-panel">
          <div className="user-info" onClick={() => setShowProfile(username)}>
            <div className="avatar-wrapper">
              <div className="avatar">{username.charAt(0).toUpperCase()}</div>
              <div className="status-dot"></div>
            </div>
            <div className="user-text">
              <span className="username-display">{username}</span>
              <span className="user-status-text">En línea</span>
            </div>
          </div>
          <div className="panel-actions">
            {inVoiceChannel && (
              <button className="icon-btn" onClick={toggleMic}>
                {micMuted ? <MicOff size={18} color="var(--danger)" /> : <Mic size={18} />}
              </button>
            )}
            <button className="icon-btn" onClick={() => setShowSettings(true)}>
              <Settings size={18} />
            </button>
          </div>
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="main-area">
        <div className="chat-header">
          <div className="header-title">
            {isDM ? (
              <>
                <div style={{ width: 24, height: 24, borderRadius: '50%', backgroundColor: 'var(--primary)', marginRight: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '12px', color: 'white', fontWeight: 'bold' }}>
                  {dmTarget.charAt(0).toUpperCase()}
                </div>
                {dmTarget}
              </>
            ) : (
              <>
                <Hash size={24} style={{ color: 'var(--text-muted)', marginRight: '8px' }} />
                {activeChannel}
              </>
            )}
          </div>
          <div className="header-actions">
            <Users size={20} style={{ color: 'var(--text-muted)', cursor: 'pointer' }} />
            <div style={{ position: 'relative' }}>
              <input type="text" placeholder="Buscar" style={{ background: 'var(--bg-darker)', border: 'none', color: 'white', padding: '4px 8px', borderRadius: '4px', width: '144px', outline: 'none' }} />
              <Search size={14} style={{ position: 'absolute', right: '8px', top: '6px', color: 'var(--text-muted)' }} />
            </div>
          </div>
        </div>

        {/* Voice Call UI */}
        {inVoiceChannel && (
          <div className="call-container" style={{ height: '220px' }}>
            <div style={{ padding: '0.5rem 1rem', background: 'var(--bg-darker)', fontSize: '0.8rem', fontWeight: 'bold' }}>
              Conectado a voz: {inVoiceChannel}
            </div>
            <div className="video-grid" style={{ overflowX: 'auto', justifyContent: 'flex-start' }}>
              {voiceUsers.map((user, i) => (
                <div key={user.id} className={`video-participant ${user.id === socket.id && !micMuted ? 'speaking' : ''}`} style={{ width: '150px', height: '120px' }}>
                  <div className="participant-avatar" style={{ width: '60px', height: '60px', fontSize: '1.5rem', backgroundColor: user.id === socket.id ? 'var(--primary)' : 'var(--success)' }}>
                    {user.name.charAt(0).toUpperCase()}
                  </div>
                  <div className="participant-name" style={{ bottom: '0.5rem' }}>{user.name} {user.id === socket.id ? "(Tú)" : ""}</div>
                </div>
              ))}
            </div>
            <div className="call-controls" style={{ height: '50px' }}>
              <button className="call-btn" onClick={toggleMic}>
                {micMuted ? <MicOff size={20} color="var(--danger)" /> : <Mic size={20} />}
              </button>
              <button className="call-btn danger" onClick={leaveVoiceChannel}>
                <PhoneOff size={20} />
              </button>
            </div>
          </div>
        )}

        <div className="messages-wrapper">
          <div className="chat-content">
            <div className="messages-scroll">
              <div style={{ padding: '2rem 1rem', marginTop: 'auto' }}>
                {isDM ? (
                  <>
                    <div style={{ width: 68, height: 68, borderRadius: '50%', backgroundColor: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '1rem', color: 'white', fontSize: '2rem', fontWeight: 'bold' }}>
                      {dmTarget.charAt(0).toUpperCase()}
                    </div>
                    <h1 style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>{dmTarget}</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Este es el comienzo de tu historial de mensajes directos con <strong>{dmTarget}</strong>.</p>
                  </>
                ) : (
                  <>
                    <div style={{ width: 68, height: 68, borderRadius: '50%', backgroundColor: 'var(--bg-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '1rem' }}>
                      <Hash size={40} color="white" />
                    </div>
                    <h1 style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>Bienvenido a #{activeChannel}!</h1>
                    <p style={{ color: 'var(--text-secondary)' }}>Este es el comienzo del canal #{activeChannel}.</p>
                  </>
                )}
              </div>

              {messages.map((msg, index) => {
                const showHeader = index === 0 || messages[index - 1].sender_id !== msg.sender_id || 
                  (new Date(msg.timestamp) - new Date(messages[index - 1].timestamp) > 300000);
                
                return (
                  <div key={msg.id || index} className="message-group">
                    {showHeader ? (
                      <div className="msg-avatar" onClick={() => setShowProfile(msg.sender_name)}>
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
                          <span className="msg-author" onClick={() => setShowProfile(msg.sender_name)}>{msg.sender_name}</span>
                          <span className="msg-timestamp">{formatTime(msg.timestamp)}</span>
                        </div>
                      )}
                      <div className="msg-content">
                        {msg.type === 'image' ? (
                          <img src={msg.content} alt="User Upload" className="msg-image" />
                        ) : (
                          msg.content
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            {typingUsers.length > 0 && (
              <div className="typing-indicator">
                {typingUsers.join(', ')} {typingUsers.length === 1 ? 'está' : 'están'} escribiendo...
              </div>
            )}

            <div className="input-area" style={{ position: 'relative' }}>
              {showEmojiPicker && (
                <div style={{ position: 'absolute', bottom: '100%', right: '1rem', marginBottom: '0.5rem', zIndex: 100 }}>
                  <EmojiPicker theme="dark" onEmojiClick={onEmojiClick} />
                </div>
              )}
              <div className="input-wrapper">
                <button 
                  onClick={() => fileInputRef.current?.click()}
                  style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', marginRight: '1rem', padding: '0.2rem' }}
                  title="Enviar Imagen"
                >
                  <Plus size={24} />
                  <input 
                    type="file" 
                    accept="image/*" 
                    style={{ display: 'none' }} 
                    ref={fileInputRef} 
                    onChange={handleImageUpload} 
                  />
                </button>
                <textarea
                  className="input-field"
                  placeholder={`Enviar mensaje a ${isDM ? dmTarget : '#' + activeChannel}`}
                  value={inputMessage}
                  onChange={handleTyping}
                  onKeyDown={handleKeyDown}
                  rows={1}
                />
                <div className="input-actions">
                  <button 
                    onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                    style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
                  >
                    <Smile size={20} />
                  </button>
                  <button 
                    onClick={handleSendMessage}
                    style={{ background: 'transparent', border: 'none', color: 'var(--primary)', cursor: 'pointer', opacity: inputMessage.trim() ? 1 : 0.5 }}
                  >
                    <Send size={20} />
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Members Sidebar */}
          {!isDM && (
            <div className="members-sidebar">
              <div className="members-list">
                <div className="member-category">En línea — {activeUsers.length}</div>
                {activeUsers.map(user => (
                  <div key={user.id} className="member-item" onClick={() => setShowProfile(user.name)}>
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
          )}
        </div>
      </div>

      {/* Settings Modal */}
      {showSettings && (
        <div className="modal-overlay" onClick={() => setShowSettings(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={{ display: 'flex', maxWidth: '800px', height: '80vh', padding: 0 }}>
            <div style={{ width: '220px', backgroundColor: 'var(--bg-darker)', padding: '2rem 1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-secondary)', paddingLeft: '0.5rem', marginBottom: '0.5rem' }}>AJUSTES DE USUARIO</div>
              <div className="channel-item active"><User size={16} className="channel-icon"/> Mi Cuenta</div>
              <div className="channel-item"><Shield size={16} className="channel-icon"/> Privacidad</div>
              <div style={{ borderTop: '1px solid var(--border)', margin: '0.5rem 0' }}></div>
              <div className="channel-item" style={{ color: 'var(--danger)' }} onClick={() => {
                socket.disconnect();
                window.location.reload();
              }}>
                <LogOut size={16} className="channel-icon"/> Cerrar Sesión
              </div>
            </div>
            <div style={{ flex: 1, padding: '2rem', backgroundColor: 'var(--bg-base)', position: 'relative' }}>
              <button style={{ position: 'absolute', top: '1rem', right: '1rem', background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }} onClick={() => setShowSettings(false)}>
                ✕ ESC
              </button>
              <h2 style={{ marginBottom: '1.5rem' }}>Mi Cuenta</h2>
              <div style={{ backgroundColor: 'var(--bg-dark)', borderRadius: '8px', padding: '1rem' }}>
                <div style={{ height: '100px', backgroundColor: 'var(--primary)', borderRadius: '8px 8px 0 0', margin: '-1rem -1rem 1rem -1rem' }}></div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: '-40px' }}>
                  <div className="avatar" style={{ width: 80, height: 80, fontSize: '2rem', border: '6px solid var(--bg-dark)' }}>
                    {username.charAt(0).toUpperCase()}
                  </div>
                </div>
                <div style={{ marginTop: '1rem', backgroundColor: 'var(--bg-darker)', borderRadius: '8px', padding: '1rem' }}>
                  <div className="form-group" style={{ marginBottom: 0 }}>
                    <label className="form-label">NUEVO NOMBRE DE USUARIO</label>
                    <div style={{ display: 'flex', gap: '1rem' }}>
                      <input 
                        type="text" 
                        className="form-input" 
                        value={newUsernameInput} 
                        onChange={(e) => setNewUsernameInput(e.target.value)} 
                      />
                      <button className="btn btn-primary" style={{ width: 'auto' }} onClick={saveSettings}>Guardar</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Profile Popout */}
      {showProfile && (
        <div className="modal-overlay" style={{ background: 'rgba(0,0,0,0.4)' }} onClick={() => setShowProfile(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()} style={{ width: '300px', padding: 0, overflow: 'hidden', position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', animation: 'scaleIn 0.2s ease-out' }}>
            <div style={{ height: '60px', backgroundColor: 'var(--primary)' }}></div>
            <div style={{ padding: '1rem', backgroundColor: 'var(--bg-dark)', position: 'relative' }}>
              <div className="avatar" style={{ width: 80, height: 80, fontSize: '2rem', border: '6px solid var(--bg-dark)', position: 'absolute', top: '-40px' }}>
                {showProfile.charAt(0).toUpperCase()}
              </div>
              <div style={{ marginTop: '40px', backgroundColor: 'var(--bg-darker)', borderRadius: '8px', padding: '1rem' }}>
                <h3 style={{ margin: 0 }}>{showProfile}</h3>
                <div style={{ borderTop: '1px solid var(--border)', margin: '0.5rem 0' }}></div>
                <div className="form-label">MIEMBRO DE DEVCHAT DESDE</div>
                <div style={{ fontSize: '0.85rem' }}>{new Date().toLocaleDateString()}</div>
                
                {showProfile === username ? (
                  <button className="btn btn-primary" style={{ marginTop: '1rem' }} onClick={() => { setShowProfile(null); setShowSettings(true); }}>Editar Perfil</button>
                ) : (
                  <button className="btn btn-primary" style={{ marginTop: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }} onClick={() => startDM(showProfile)}>
                    Enviar Mensaje Directo
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
