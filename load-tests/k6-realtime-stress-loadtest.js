// Prueba de carga #2 contra produccion real de OrcaLab (https://orcalab.online).
//
// A diferencia de la prueba anterior (k6-marcador-loadtest.js), esta NO mide
// login: auth-service ya quedo identificado como el cuello de botella actual
// (ver corrida anterior, degradacion sostenida desde ~60-70 VUs concurrentes
// en /api/auth/login). Esta prueba aisla el canal de tiempo real
// (realtime-service: broker STOMP en memoria via enableSimpleBroker) para
// encontrar SU propio limite, sin que el login lo contamine.
//
// Diseno:
//   - setup() autentica un pool de usuarios UNA SOLA VEZ (login, no en cada
//     iteracion) y guarda los tokens. Tambien crea la sala compartida y
//     descubre el canal de texto por defecto ("general", se crea solo via
//     evento SalaCreada -> RoomEventConsumer, con ~1s de retraso porque el
//     consumidor hace poll de Redis Streams cada 1000ms; por eso hay reintentos).
//   - Cada iteracion de VU = una sesion WS/STOMP persistente de
//     SESSION_DURATION_MS: conecta, se suscribe a chat y a marcadores, y
//     durante la sesion manda mensajes de chat cada CHAT_INTERVAL_MS y
//     marcadores cada MARKER_INTERVAL_MS. Cero HTTP durante la sesion.
//   - La confirmacion de "el broker realmente entrego esto" es por eco: cada
//     mensaje/marcador lleva un id de correlacion propio (VU-iter-contador) y
//     se mide el tiempo hasta que ese mismo VU ve el eco en su propia
//     suscripcion al topic compartido.
//
// ADVERTENCIA DE DISEÑO (fan-out O(n) por publish): la sala es compartida
// entre TODOS los VUs, asi que cada mensaje que un VU publica se reenvia a
// TODOS los VUs conectados. A 800 VUs con chat cada 2s eso es ~400 msg/s
// publicados x 800 suscriptores = ~320k entregas/s solo de chat (mas
// marcadores). Es intencional -- asi es como se ve una sala real con mucha
// gente conectada -- pero significa que el propio k6 (parseo de esos
// mensajes en 800 VUs) tambien puede volverse el cuello de botella del lado
// cliente. Si la latencia se dispara, vale la pena mirar tambien CPU/red de
// esta maquina, no asumir que el limite es 100% del lado servidor.
//
// Uso:
//   Smoke test (20 VUs, 30s):   k6 run -e SMOKE=1 k6-realtime-stress-loadtest.js
//   Corrida completa (stages):  k6 run k6-realtime-stress-loadtest.js

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

const SESSION_DURATION_MS = 11000; // no multiplo exacto de los intervalos: deja margen para el eco del ultimo envio antes de cerrar
const CHAT_INTERVAL_MS = 2000;
const MARKER_INTERVAL_MS = 5000;
const CONNECT_TIMEOUT_MS = 15000;

const wsConnectTrend = new Trend('ws_connect_duration', true);
const wsConnectFailedRate = new Rate('ws_connect_failed');
const chatTrend = new Trend('ws_chat_duration', true);
const chatFailedRate = new Rate('ws_chat_failed');
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
    // Informativos (no HTTP: esta prueba no hace requests durante la corrida
    // salvo en setup, que va tageado aparte y no cuenta aca).
    ws_chat_failed: ['rate<0.05'],
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
  const salaNombre = 'LoadTest-Realtime ' + new Date().toISOString();
  const salaRes = http.post(
    BASE_URL + '/api/salas',
    JSON.stringify({
      nombre: salaNombre,
      descripcion: 'Sala generada por k6 para prueba de estres del broker STOMP - eliminar tras la corrida',
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

  // El canal "general" lo crea RoomEventConsumer via evento SalaCreada leido
  // de un stream de Redis con poll cada 1000ms - no es instantaneo. Reintentar.
  let canalId = null;
  for (let intento = 0; intento < 10 && !canalId; intento++) {
    sleep(1);
    const canalesRes = http.get(BASE_URL + '/api/salas/' + salaId + '/canales', {
      headers: { Authorization: 'Bearer ' + leaderToken },
      tags: { name: 'setup_listar_canales' },
    });
    if (canalesRes.status === 200) {
      const canales = canalesRes.json();
      if (canales && canales.length > 0) {
        canalId = canales[0].id;
      }
    }
  }
  if (!canalId) {
    throw new Error('El canal por defecto de la sala ' + salaId + ' nunca aparecio tras 10s de reintentos.');
  }

  console.log(
    '>>> Setup listo: ' + tokens.length + ' usuarios autenticados, sala id=' + salaId +
    ' nombre="' + salaNombre + '" canal id=' + canalId + ' (usar en cleanup-sala.ps1)'
  );

  return { salaId: salaId, canalId: canalId, tokens: tokens };
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

function realtimeSession(token, salaId, canalId) {
  const serverId = String(Math.floor(Math.random() * 1000)).padStart(3, '0');
  const sessionId = randomString(8);
  const url = WS_BASE_URL + '/' + serverId + '/' + sessionId + '/websocket';

  const sessionTag = __VU + '-' + __ITER + '-' + Date.now();
  const connectStart = Date.now();

  let connected = false;
  let chatCounter = 0;
  let markerCounter = 0;
  const pendingChat = {}; // corrId -> sentAt
  const pendingMarker = {}; // corrId -> sentAt

  function settlePending(map, rateMetric, asFailure) {
    const keys = Object.keys(map);
    for (let i = 0; i < keys.length; i++) {
      rateMetric.add(asFailure ? 1 : 0);
      delete map[keys[i]];
    }
  }

  const res = ws.connect(url, {}, function (socket) {
    let chatTimer = null;
    let markerTimer = null;

    socket.setTimeout(function () {
      if (!connected) wsConnectFailedRate.add(1);
      settlePending(pendingChat, chatFailedRate, true);
      settlePending(pendingMarker, marcadorFailedRate, true);
      socket.close();
    }, CONNECT_TIMEOUT_MS);

    function enviarChat() {
      const corrId = 'k6chat-' + sessionTag + '-' + chatCounter++;
      pendingChat[corrId] = Date.now();
      const body = JSON.stringify({ contenido: corrId, marcadorId: null });
      const frame =
        'SEND\ndestination:/app/sala/' + salaId + '/canal/' + canalId + '/mensaje\ncontent-type:application/json\n\n' +
        body + '\x00';
      socket.send(JSON.stringify([frame]));
    }

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
              'SUBSCRIBE\nid:sub-chat\ndestination:/topic/sala/' + salaId + '/canal/' + canalId + '/chat\n\n\x00',
            ]));
            socket.send(JSON.stringify([
              'SUBSCRIBE\nid:sub-marcador\ndestination:/topic/sala/' + salaId + '/marcadores\n\n\x00',
            ]));

            // Primer envio inmediato, despues a intervalo regular.
            enviarChat();
            enviarMarcador();
            chatTimer = socket.setInterval(enviarChat, CHAT_INTERVAL_MS);
            markerTimer = socket.setInterval(enviarMarcador, MARKER_INTERVAL_MS);

            socket.setTimeout(function () {
              if (chatTimer) socket.clearInterval(chatTimer);
              if (markerTimer) socket.clearInterval(markerTimer);
              // Lo que quedo pendiente sin eco al cerrar cuenta como fallo
              // (timeout de entrega, no necesariamente error del servidor).
              settlePending(pendingChat, chatFailedRate, true);
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

            if (payload.contenido && Object.prototype.hasOwnProperty.call(pendingChat, payload.contenido)) {
              const sentAt = pendingChat[payload.contenido];
              delete pendingChat[payload.contenido];
              chatTrend.add(Date.now() - sentAt);
              chatFailedRate.add(0);
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
            settlePending(pendingChat, chatFailedRate, true);
            settlePending(pendingMarker, marcadorFailedRate, true);
            socket.close();
          }
        });
      }
    });

    socket.on('error', function () {
      if (!connected) wsConnectFailedRate.add(1);
      settlePending(pendingChat, chatFailedRate, true);
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
  realtimeSession(token, data.salaId, data.canalId);
  sleep(Math.random() * 1.5); // jitter para no reconectar todos los VUs en el mismo instante
}
