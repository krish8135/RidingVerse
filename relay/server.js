/**
 * RidingVerse convoy telemetry relay.
 *
 * Rooms are keyed by convoy code (RV-####). The relay is intentionally dumb:
 * it authenticates nothing and stores nothing — it just routes JSON packets
 * between riders in the same room.
 *
 * Packet types (all JSON text frames):
 *   join      { type, riderId, room, name?, color? }  -> joins room, peers notified
 *   leave     { type, riderId }                       -> leaves room, peers notified
 *   location  { type, riderId, lat, lng, speedMps?, bearingDeg?, name?, color? }
 *   telemetry { type, riderId, ... }                  -> broadcast to room
 *   heartbeat { type, riderId }                       -> keeps the peer alive
 *   sos       { type, riderId, lat?, lng? }           -> broadcast to room
 *   ptt       { v, type:'ptt', room, riderId, seq, codec, audio } -> voice frame
 *   roster                                          <- server -> joiner: current peers
 *
 * Run:  npm install && node server.js   (PORT env, default 8080)
 */

const WebSocket = require('ws');

const PORT = parseInt(process.env.PORT || '8080', 10);
const HEARTBEAT_TIMEOUT_MS = 45_000;
const MAX_FRAME_BYTES = 256 * 1024; // enough for a ~40ms PCM voice frame
const ROOM_CODE = /^RV-\d{4}$/;

/** roomCode -> Map(ws -> { riderId, name, color, lastSeen }) */
const rooms = new Map();

function getRoom(code) {
  let room = rooms.get(code);
  if (!room) {
    room = new Map();
    rooms.set(code, room);
  }
  return room;
}

/** Send `obj` to everyone in the room except `sender` (null = everyone). */
function broadcast(code, sender, obj) {
  const room = rooms.get(code);
  if (!room) return;
  const payload = JSON.stringify(obj);
  for (const ws of room.keys()) {
    if (ws !== sender && ws.readyState === WebSocket.OPEN) {
      ws.send(payload);
    }
  }
}

function rosterFor(code, exclude) {
  const room = rooms.get(code);
  if (!room) return [];
  const peers = [];
  for (const [ws, info] of room.entries()) {
    if (ws === exclude) continue;
    peers.push({
      riderId: info.riderId,
      name: info.name,
      color: info.color,
      lastSeen: info.lastSeen,
    });
  }
  return peers;
}

function removeFromRoom(ws, info) {
  const { roomCode } = info;
  if (!roomCode) return;
  const room = rooms.get(roomCode);
  if (room && room.delete(ws)) {
    broadcast(roomCode, ws, { type: 'leave', riderId: info.riderId });
    if (room.size === 0) rooms.delete(roomCode);
  }
  info.roomCode = null;
}

const wss = new WebSocket.Server({ port: PORT });

wss.on('connection', (ws) => {
  const info = { roomCode: null, riderId: null, name: null, color: null, lastSeen: Date.now() };
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (data, isBinary) => {
    if (isBinary) return; // voice uses base64 text frames, see PttManager protocol
    if (data.length > MAX_FRAME_BYTES) return;

    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return;
    }
    if (!msg || typeof msg.type !== 'string') return;
    info.lastSeen = Date.now();

    switch (msg.type) {
      case 'join': {
        if (typeof msg.room !== 'string' || !ROOM_CODE.test(msg.room)) {
          ws.send(JSON.stringify({ type: 'error', message: 'bad room code, want RV-####' }));
          return;
        }
        if (info.roomCode) removeFromRoom(ws, info);
        info.roomCode = msg.room;
        info.riderId = String(msg.riderId || 'unknown');
        info.name = msg.name || null;
        info.color = msg.color ?? null;
        getRoom(msg.room).set(ws, info);
        // Tell the joiner who's already here…
        ws.send(JSON.stringify({ type: 'roster', peers: rosterFor(msg.room, ws) }));
        // …and tell everyone else about the joiner.
        broadcast(msg.room, ws, {
          type: 'join',
          riderId: info.riderId,
          name: info.name,
          color: info.color,
        });
        break;
      }

      case 'leave': {
        removeFromRoom(ws, info);
        break;
      }

      case 'location':
      case 'telemetry':
      case 'heartbeat':
      case 'sos': {
        if (!info.roomCode) return;
        broadcast(info.roomCode, ws, msg);
        break;
      }

      case 'ptt': {
        // Voice frame: rebroadcast to the room, never back to the sender.
        const room = msg.room || info.roomCode;
        if (!room) return;
        broadcast(room, ws, msg);
        break;
      }

      default:
        // Unknown packet types are ignored, not forwarded.
        break;
    }
  });

  ws.on('close', () => removeFromRoom(ws, info));
  ws.on('error', () => removeFromRoom(ws, info));
});

// Drop dead TCP connections (ws ping/pong)…
setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30_000).unref();

// …and prune peers that stopped heartbeating so rosters stay truthful.
setInterval(() => {
  const now = Date.now();
  for (const [code, room] of rooms.entries()) {
    for (const [ws, peer] of room.entries()) {
      if (now - peer.lastSeen > HEARTBEAT_TIMEOUT_MS) {
        room.delete(ws);
        broadcast(code, ws, { type: 'leave', riderId: peer.riderId });
        try { ws.terminate(); } catch { /* already gone */ }
      }
    }
    if (room.size === 0) rooms.delete(code);
  }
}, 15_000).unref();

wss.on('listening', () => {
  console.log(`RidingVerse relay listening on :${PORT}`);
});
