// Prueba de carga #3 contra produccion real de OrcaLab (https://orcalab.online).
//
// Variante de k6-realtime-stress-loadtest.js que aisla SOLO la creacion de
// marcadores, sin chat de fondo, para confirmar si el chat (alta frecuencia,
// cada 2s en la prueba anterior) es el que realmente satura el broker STOMP,
// o si los marcadores por si solos (mas baja frecuencia, 3-5s) se comportan
// distinto. Mismo diseno de setup (tokens reusados, cero logins durante la
// corrida) y mismo framing SockJS+STOMP ya usado en los otros scripts.
//
// Diferencia clave respecto a la prueba con chat+marcador:
//   - Ningun mensaje de chat, ninguna suscripcion al topic de chat.
//   - Intervalo de marcador con jitter 3-5s (no fijo) via setTimeout
//     recursivo, en vez de socket.setInterval de periodo fijo - mas parecido
//     a como un usuario real reporta avistamientos (no a un ritmo constante).
//
// Uso:
//   Smoke test (20 VUs, 30s):   k6 run -e SMOKE=1 k6-marcador-only-loadtest.js
//   Corrida completa (stages):  k6 run k6-marcador-only-loadtest.js

import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = 'https://orcalab.online';
const WS_BASE_URL = 'wss://orcalab.online/ws';

const NUM_USERS = 100;
const USERS = Array.from({ length: NUM_USERS }, (_, i) => ({
  email: `loadtest${i + 1}@orcalab.local`,
  password: 'LoadTest123!',
  nombre: `Load Test ${i + 1}`,
}));

const SESSION_DURATION_MS = 14000; // margen para 2-3 envios con jitter 3-5s + eco del ultimo antes de cerrar
const MARKER_INTERVAL_MIN_MS = 3000;
const MARKER_INTERVAL_MAX_MS = 5000;
const CONNECT_TIMEOUT_MS = 15000;

const wsConnectTrend = new Trend('ws_connect_duration', true);
const wsConnectFailedRate = new Rate('ws_connect_failed');
const marcadorTrend = new Trend('ws_marcador_duration', true);
const marcadorFailedRate = new Rate('ws_marcador_failed');

const isSmoke = __ENV.SMOKE === '1';

export const options = {
  stages: isSmoke
    ? [{ duration: '30s', target: 20 }]
    : [
        { duration: '1m', target: 100 },
        { duration: '2m', target: 300 },
        { duration: '2m', target: 500 },
        { duration: '2m', target: 800 },
        { duration: '1m', target: 0 },
      ],
  thresholds: {
    // Informativo (no HTTP: esta prueba no hace requests durante la corrida
    // salvo en setup, que va tageado aparte y no cuenta aca).
    ws_marcador_failed: ['rate<0.05'],
  },
};

function randomString(len) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let s = '';
  for (let i = 0; i < len; i++) s += chars[Math.floor(Math.random() * chars.length)];
  return s;
}

function registrar(user) {
  const res = http.post(BASE_URL + '/api/auth/registro', JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'setup_registro' },
    responseCallback: http.expectedStatuses(201, 400),
  });
  if (res.status !== 201 && res.status !== 400) {
    console.error('No se pudo registrar ' + user.email + ': ' + res.status + ' ' + res.body);
  }
}

function login(user) {
  return http.post(
    BASE_URL + '/api/auth/login',
    JSON.stringify({ email: user.email, password: user.password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'setup_login' },
    }
  );
}

export function setup() {
  USERS.forEach(registrar);

  const tokens = [];
  USERS.forEach((user) => {
    const res = login(user);
    if (res.status === 200) {
      tokens.push(res.json('token'));
    } else {
      console.error('No se pudo loguear ' + user.email + ' en setup: ' + res.status);
    }
  });

  if (tokens.length === 0) {
    throw new Error('No se pudo autenticar a ningun usuario en setup, abortando.');
  }

  const leaderToken = tokens[0];
  const salaNombre = 'LoadTest-SoloMarcador ' + new Date().toISOString();
  const salaRes = http.post(
    BASE_URL + '/api/salas',
    JSON.stringify({
      nombre: salaNombre,
      descripcion: 'Sala generada por k6 para aislar el costo de marcadores sin chat - eliminar tras la corrida',
    }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + leaderToken },
      tags: { name: 'setup_crear_sala' },
    }
  );
  const salaOk = check(salaRes, { 'setup: crear sala ok': (r) => r.status === 201 });
  if (!salaOk) {
    throw new Error('No se pudo crear la sala en setup: ' + salaRes.status + ' ' + salaRes.body);
  }
  const salaId = salaRes.json('id');

  console.log(
    '>>> Setup listo: ' + tokens.length + ' usuarios autenticados, sala id=' + salaId +
    ' nombre="' + salaNombre + '" (usar en cleanup-sala.ps1)'
  );

  return { salaId: salaId, tokens: tokens };
}

// Parsea un frame STOMP crudo (ya sin el prefijo SockJS) en { command, body }.
function parseStompFrame(frame) {
  const nullIdx = frame.indexOf('\x00');
  const raw = nullIdx >= 0 ? frame.slice(0, nullIdx) : frame;
  const sep = raw.indexOf('\n\n');
  const head = sep >= 0 ? raw.slice(0, sep) : raw;
  const body = sep >= 0 ? raw.slice(sep + 2) : '';
  return { command: head.split('\n')[0], body: body };
}

function realtimeSession(token, salaId) {
  const serverId = String(Math.floor(Math.random() * 1000)).padStart(3, '0');
  const sessionId = randomString(8);
  const url = WS_BASE_URL + '/' + serverId + '/' + sessionId + '/websocket';

  const sessionTag = __VU + '-' + __ITER + '-' + Date.now();
  const connectStart = Date.now();

  let connected = false;
  let markerCounter = 0;
  const pendingMarker = {}; // corrId -> sentAt

  function settlePending(map, rateMetric, asFailure) {
    const keys = Object.keys(map);
    for (let i = 0; i < keys.length; i++) {
      rateMetric.add(asFailure ? 1 : 0);
      delete map[keys[i]];
    }
  }

  const res = ws.connect(url, {}, function (socket) {
    let sessionEnded = false;

    socket.setTimeout(function () {
      if (!connected) wsConnectFailedRate.add(1);
      settlePending(pendingMarker, marcadorFailedRate, true);
      socket.close();
    }, CONNECT_TIMEOUT_MS);

    function enviarMarcador() {
      const corrId = 'k6marker-' + sessionTag + '-' + markerCounter++;
      pendingMarker[corrId] = Date.now();
      const body = JSON.stringify({
        id: null,
        latitud: -23.65 + Math.random() * 0.1,
        longitud: -70.4 + Math.random() * 0.1,
        tipo: 'AVISTAMIENTO',
        descripcion: corrId,
      });
      const frame =
        'SEND\ndestination:/app/sala/' + salaId + '/marcador\ncontent-type:application/json\n\n' + body + '\x00';
      socket.send(JSON.stringify([frame]));
    }

    // Jitter 3-5s en vez de intervalo fijo: se reprograma a si misma con un
    // delay aleatorio nuevo cada vez, mas parecido a reportes reales que a
    // un metronomo.
    function enviarMarcadorLoop() {
      if (sessionEnded) return;
      enviarMarcador();
      const proximoDelay = MARKER_INTERVAL_MIN_MS + Math.random() * (MARKER_INTERVAL_MAX_MS - MARKER_INTERVAL_MIN_MS);
      socket.setTimeout(enviarMarcadorLoop, proximoDelay);
    }

    socket.on('message', function (msg) {
      if (msg.length === 0) return;
      const tipo = msg.charAt(0);

      if (tipo === 'o') {
        const connectFrame =
          'CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer ' + token + '\n\n\x00';
        socket.send(JSON.stringify([connectFrame]));
        return;
      }

      if (tipo === 'h') return;
      if (tipo === 'c') {
        socket.close();
        return;
      }

      if (tipo === 'a') {
        let rawFrames;
        try {
          rawFrames = JSON.parse(msg.slice(1));
        } catch (e) {
          return;
        }

        rawFrames.forEach(function (rawFrame) {
          const parsed = parseStompFrame(rawFrame);

          if (!connected && parsed.command === 'CONNECTED') {
            connected = true;
            wsConnectTrend.add(Date.now() - connectStart);
            wsConnectFailedRate.add(0);

            socket.send(JSON.stringify([
              'SUBSCRIBE\nid:sub-marcador\ndestination:/topic/sala/' + salaId + '/marcadores\n\n\x00',
            ]));

            // Primer envio inmediato, despues con jitter 3-5s.
            enviarMarcadorLoop();

            socket.setTimeout(function () {
              sessionEnded = true;
              // Lo que quedo pendiente sin eco al cerrar cuenta como fallo
              // (timeout de entrega, no necesariamente error del servidor).
              settlePending(pendingMarker, marcadorFailedRate, true);
              socket.close();
            }, SESSION_DURATION_MS);

            return;
          }

          if (parsed.command === 'MESSAGE') {
            let payload;
            try {
              payload = JSON.parse(parsed.body);
            } catch (e) {
              return;
            }

            if (payload.descripcion && Object.prototype.hasOwnProperty.call(pendingMarker, payload.descripcion)) {
              const sentAt = pendingMarker[payload.descripcion];
              delete pendingMarker[payload.descripcion];
              marcadorTrend.add(Date.now() - sentAt);
              marcadorFailedRate.add(0);
              return;
            }
            return;
          }

          if (parsed.command === 'ERROR') {
            settlePending(pendingMarker, marcadorFailedRate, true);
            socket.close();
          }
        });
      }
    });

    socket.on('error', function () {
      if (!connected) wsConnectFailedRate.add(1);
      settlePending(pendingMarker, marcadorFailedRate, true);
    });
  });

  check(res, { 'ws: upgrade 101': (r) => r && r.status === 101 });
  if (!res || res.status !== 101) {
    wsConnectFailedRate.add(1);
  }
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];
  realtimeSession(token, data.salaId);
  sleep(Math.random() * 1.5); // jitter para no reconectar todos los VUs en el mismo instante
}
