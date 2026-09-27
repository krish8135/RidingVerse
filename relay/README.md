# RidingVerse Relay

Minimal WebSocket relay for convoy telemetry. Rooms are keyed by convoy code
(`RV-####`); the relay broadcasts `location` / `telemetry` / `heartbeat` /
`join` / `leave` / `sos` / `ptt` packets to every rider in the same room,
prunes dead peers, and stores nothing.

## Run locally

```bash
cd relay
npm install
node server.js          # listens on :8080 (PORT env overrides)
```

Test it with two terminals:

```bash
# terminal 1 — join as rider A
node -e "const W=require('ws');const s=new W('ws://localhost:8080');s.on('open',()=>s.send(JSON.stringify({type:'join',riderId:'a',room:'RV-9042',name:'Asha'})));s.on('message',m=>console.log('A got:',m.toString()))"
# terminal 2 — join as rider B and send a location
node -e "const W=require('ws');const s=new W('ws://localhost:8080');s.on('open',()=>{s.send(JSON.stringify({type:'join',riderId:'b',room:'RV-9042',name:'Dev'}));setTimeout(()=>s.send(JSON.stringify({type:'location',riderId:'b',room:'RV-9042',lat:12.97,lng:77.59})),500)});s.on('message',m=>console.log('B got:',m.toString()))"
```

## Deploy — fly.io (one command after `fly auth login`)

```bash
cd relay
fly launch --no-deploy   # accept defaults; it detects the Dockerfile
fly deploy               # note the https://<app>.fly.dev URL
```

Your relay URL for the app is `wss://<app>.fly.dev` (fly terminates TLS;
the container still listens on plain `:8080`).

## Deploy — render.com (dashboard, ~2 minutes)

1. **New → Web Service**, connect this repo.
2. Root directory: `relay`. Build command: `npm install`. Start command: `node server.js`.
3. Deploy. Render sets `PORT` automatically and gives you
   `https://<service>.onrender.com`.

Your relay URL for the app is `wss://<service>.onrender.com`.

## Point the app at it

In the app: **Settings → Relay server**, paste the `wss://…` URL and save.
Both riders must use the same relay URL and the same room code.
